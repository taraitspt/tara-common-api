#!/bin/bash
# tara-common-api(공용 API 서버) EC2 초기 세팅 — Ubuntu 22.04/24.04, 최초 1회.
# 로컬에서 `./deploy.sh setup` 이 이 폴더를 올리고 sudo 로 실행한다. 서버에서 직접 `sudo bash setup.sh` 해도 된다.
# 다시 실행해도 안전하다: 이미 있는 DB·계정·환경변수 파일(비밀값)은 건드리지 않는다.
# hrm_module/infra/ec2/setup.sh 를 본떴다 — 구조를 바꾸면 그쪽도 함께 확인한다.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then echo "sudo 로 실행하세요." >&2; exit 1; fi
HERE="$(cd "$(dirname "$0")" && pwd)"

APP_USER=tara
APP_DIR=/opt/tara-common-api
ENV_DIR=/etc/tara-common-api
ENV_FILE=$ENV_DIR/tara-common-api.env
DB_NAME=tara_common
DB_USER=tara

echo "===== [1/6] 시간대 Asia/Seoul ====="
timedatectl set-timezone Asia/Seoul

echo "===== [2/6] 스왑 2G (없을 때만) ====="
# 작은 인스턴스에서 Java + MariaDB 가 같이 돌면 메모리가 모자라 OOM 으로 죽는다.
if ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "===== [3/6] 패키지 설치 ====="
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y openjdk-17-jre-headless nginx mariadb-server curl openssl

echo "===== [4/6] 앱 사용자·폴더 ====="
id "$APP_USER" >/dev/null 2>&1 || useradd --system --home-dir "$APP_DIR" --shell /usr/sbin/nologin "$APP_USER"
install -d -o "$APP_USER" -g "$APP_USER" -m 750 "$APP_DIR"
install -d -o root -g "$APP_USER" -m 750 "$ENV_DIR"

echo "===== [5/6] MariaDB (로컬 전용) ====="
systemctl enable --now mariadb
# Ubuntu 기본 bind-address=127.0.0.1 — 외부에서 3306 으로 붙을 수 없다. 보안그룹에서도 3306 은 열지 않는다.
if [ ! -f "$ENV_FILE" ]; then
  DB_PASSWORD=$(openssl rand -hex 24)
  mariadb <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
CREATE USER IF NOT EXISTS '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

  echo "===== 환경변수 파일 $ENV_FILE (최초 1회 생성) ====="
  ADMIN_TOKEN=$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n')
  umask 027
  cat > "$ENV_FILE" <<ENV
# tara-common-api 운영 환경변수 — systemd(tara-common-api) 가 EnvironmentFile 로 읽는다. 저장소에 올리지 않는다.
# 값을 바꾸면: sudo systemctl restart tara-common-api
SERVER_PORT=8080

DB_HOST=localhost
DB_PORT=3306
DB_NAME=$DB_NAME
DB_USERNAME=$DB_USER
DB_PASSWORD=$DB_PASSWORD

# 관리 API(/api/admin/**) 토큰 — 헤더 X-Admin-Token. 사용처 등록·키 발급에 쓴다.
APP_ADMIN_TOKEN=$ADMIN_TOKEN

# MS Graph 서비스계정 (Teams 1:1 발송). hrm 과 같은 값. 하나라도 비면 발송 API 가 503.
MS_TENANT_ID=
MS_CLIENT_ID=
MS_USERNAME=
MS_PASSWORD=

# 받는 사람 허용 메일 도메인(쉼표)
APP_NOTIFY_ALLOWED_DOMAINS=taratps.com

# 접속 IP 제한(CIDR 쉼표 목록). 보안그룹이 1차, 이것이 2차 안전망. 운영은 사내망/호출 서버 대역으로 꼭 설정.
APP_IP_ALLOWLIST=
# nginx 가 X-Forwarded-For 를 \$remote_addr 로 덮어쓰므로 true 가 안전하다.
APP_TRUST_PROXY=true

# Swagger — 운영 기본 꺼짐. 잠깐 켜서 확인할 때만 true 로 바꾸고 재시작.
APP_SWAGGER_ENABLED=false
ENV
  chown root:"$APP_USER" "$ENV_FILE"
  chmod 640 "$ENV_FILE"
  umask 022
else
  echo "(이미 있음 — $ENV_FILE 과 DB 계정은 그대로 둔다)"
fi

echo "===== [6/6] systemd·nginx ====="
install -m 644 "$HERE/tara-common-api.service" /etc/systemd/system/tara-common-api.service
systemctl daemon-reload
systemctl enable tara-common-api
# jar 는 ./deploy.sh 가 처음 올린다. 그 전에는 서비스가 뜨지 않는 게 정상이다.

install -m 644 "$HERE/nginx-tara.conf" /etc/nginx/sites-available/tara-common-api
ln -sf /etc/nginx/sites-available/tara-common-api /etc/nginx/sites-enabled/tara-common-api
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable nginx
systemctl reload nginx || systemctl restart nginx

echo ""
echo "===== 세팅 완료 ====="
echo "다음: $ENV_FILE 에 MS_* 비밀값을 채운 뒤, 로컬에서 ./deploy.sh 로 첫 배포."
