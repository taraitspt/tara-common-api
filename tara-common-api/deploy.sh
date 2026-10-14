#!/bin/bash
# tara-common-api(공용 API 서버) 배포 스크립트 (SSH 방식) — Git Bash 에서 실행.
# 로컬에서 빌드 → scp 로 EC2(Ubuntu) 에 올림 → ssh 로 교체·재시작.
# hrm_module/deploy.sh 를 본떴다(API 전용이라 web 모드는 없다).
#
# 배포 대상은 소스에 박지 않는다. 프로젝트 루트의 .env.deploy.local(gitignore) 에 둔다:
#   TARA_SSH_HOST=3.x.x.x
#   TARA_SSH_KEY=C:/Users/me/.ssh/tara.pem
#   TARA_SSH_USER=ubuntu            # 생략 시 ubuntu
#
# 사용법:
#   ./deploy.sh setup    # 최초 1회: infra/ec2 를 올려 서버 세팅(Java·nginx·MariaDB·systemd)
#   ./deploy.sh          # API 빌드·배포
set -euo pipefail
cd "$(dirname "$0")"

[ -f .env.deploy.local ] && . ./.env.deploy.local
HOST="${TARA_SSH_HOST:-}"
KEY="${TARA_SSH_KEY:-}"
SSH_USER="${TARA_SSH_USER:-ubuntu}"

if [ -z "$HOST" ] || [ -z "$KEY" ]; then
  echo "TARA_SSH_HOST / TARA_SSH_KEY 가 비어 있습니다. 프로젝트 루트 .env.deploy.local 에 적으세요." >&2
  exit 1
fi
[ -f "$KEY" ] || { echo "키 파일이 없습니다: $KEY" >&2; exit 1; }

APP_DIR=/opt/tara-common-api
SERVICE_NAME=tara-common-api
JAR_NAME=tara-common-api.jar

SSH_OPTS=(-i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15)
remote() { ssh "${SSH_OPTS[@]}" "$SSH_USER@$HOST" "$@"; }
upload() { scp "${SSH_OPTS[@]}" "$@"; }

MODE="${1:-api}"
case "$MODE" in
  setup|api) ;;
  *) echo "사용법: ./deploy.sh [setup|api]" >&2; exit 1 ;;
esac

# ── 최초 서버 세팅 ──
if [ "$MODE" = "setup" ]; then
  echo "===== [SETUP] infra/ec2 업로드 ====="
  remote "rm -rf /tmp/tara-setup && mkdir -p /tmp/tara-setup"
  upload infra/ec2/* "$SSH_USER@$HOST:/tmp/tara-setup/"
  echo "===== [SETUP] 실행 (몇 분 걸린다) ====="
  remote "sudo bash /tmp/tara-setup/setup.sh"
  exit 0
fi

# ── 백엔드 ──
echo "===== [API] Gradle 빌드 ====="
./gradlew build -x test
JAR=$(ls build/libs/*.jar | grep -v -- '-plain\.jar$' | head -1)
[ -n "$JAR" ] || { echo "빌드된 jar 가 없습니다." >&2; exit 1; }

echo "===== [API] 업로드: $JAR ====="
upload "$JAR" "$SSH_USER@$HOST:/tmp/$JAR_NAME"

echo "===== [API] 교체·재시작 ====="
# 직전 jar 는 .bak 으로 남긴다 — 문제가 생기면 서버에서 되돌릴 수 있게.
remote "set -e
  [ -f $APP_DIR/$JAR_NAME ] && sudo cp -p $APP_DIR/$JAR_NAME $APP_DIR/$JAR_NAME.bak || true
  sudo install -o tara -g tara -m 640 /tmp/$JAR_NAME $APP_DIR/$JAR_NAME
  rm -f /tmp/$JAR_NAME
  sudo systemctl restart $SERVICE_NAME
  echo '기동 대기 (최대 120초)...'
  for i in \$(seq 1 60); do
    if curl -fs http://127.0.0.1:8080/actuator/health >/dev/null; then echo BACKEND_OK; exit 0; fi
    sleep 2
  done
  echo BACKEND_FAIL — 최근 로그:
  sudo journalctl -u $SERVICE_NAME -n 60 --no-pager
  exit 1"

echo ""
echo "===== 배포 완료: http://$HOST ====="
