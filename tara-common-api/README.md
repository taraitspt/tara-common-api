# tara-common-api

타라 사내 시스템들이 함께 쓰는 공용 API 서버입니다.
첫 기능은 **Teams 1:1 메시지 발송 API**로, `hrm-module-api`의 `integration/notify`, `integration/teams`를 그대로 옮겨 왔습니다.

인증은 **사용처(시스템)마다 API 키 하나**를 발급하고, 키에 **권한(scope)** 으로 쓸 수 있는 API 를 정합니다.
지금 scope 는 `notify`(Teams 발송) 하나이고, 새 공용 API 가 생기면 scope 를 추가해 같은 키 체계를 그대로 씁니다.

- Spring Boot 3.5.16 / Java 17 / Gradle 8.7 (hrm-module-api와 같음)
- DB: MariaDB(MySQL) + Flyway. `local` 프로파일은 H2 메모리 DB라 설치 없이 바로 뜹니다.
- 기본 패키지: `com.tara.commonapi`

## 바로 실행 (local, DB 없이)

```powershell
copy .env.example .env.local      # MS_* 값과 APP_ADMIN_TOKEN 을 채운다
.\run-local.ps1                   # http://localhost:8083/swagger-ui.html
```

`SPRING_PROFILES_ACTIVE=dev`로 바꾸면 로컬 MariaDB의 `tara_common` DB를 씁니다(없으면 자동 생성).

## 환경변수

| 이름 | 설명 |
|---|---|
| `MS_TENANT_ID` `MS_CLIENT_ID` `MS_USERNAME` `MS_PASSWORD` | Teams 발송용 MS Graph 서비스계정. hrm과 같은 값. 하나라도 비면 발송 API가 503 |
| `APP_ADMIN_TOKEN` | 관리 API(`/api/admin/**`) 토큰. 헤더 `X-Admin-Token`. 비우면 관리 API 전부 막힘 |
| `APP_NOTIFY_ALLOWED_DOMAINS` | 받는 사람 허용 도메인(기본 `taratps.com`) |
| `APP_IP_ALLOWLIST` | 서버 전체 접속 허용 IP/CIDR. 운영 필수 |
| `APP_TRUST_PROXY` | nginx 뒤면 `true` (prod 기본 true) |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | dev/prod DB |
| `SERVER_PORT` | 기본 8080 (로컬 예시 8083) |

## API

### 1) 사용처 등록 → API 키 발급 (관리자)

```bash
curl -X POST http://localhost:8083/api/admin/clients \
  -H "X-Admin-Token: <APP_ADMIN_TOKEN>" -H "Content-Type: application/json" \
  -d '{"clientName":"영업관리 GROW","ownerDept":"IT지원팀","ownerName":"홍길동","scopes":["notify"],"allowedIps":"10.0.0.0/8","ratePerMin":30}'
```

응답의 `apiKey`(`tara_...`)는 **이때 한 번만** 보입니다. DB에는 SHA-256 해시만 저장됩니다.
`scopes` 는 이 키로 쓸 수 있는 API 목록이고 비우면 `["notify"]` 입니다. (가능한 값은 코드의 `ApiScopes` 참고)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/admin/clients` | 사용처 목록 (권한·최근 30일 성공/실패 건수) |
| POST | `/api/admin/clients` | 등록 + 키 발급 |
| PUT | `/api/admin/clients/{seq}` | 수정 (이름·담당·권한·허용 IP·한도·사용 여부) |
| POST | `/api/admin/clients/{seq}/regenerate` | 키 재발급 (옛 키 즉시 무효) |
| GET | `/api/admin/clients/{seq}/logs?limit=100` | 발송 기록 |

### 2) Teams 메시지 보내기 (각 시스템)

```bash
curl -X POST http://localhost:8083/api/notify/teams \
  -H "X-Api-Key: tara_xxxxxxxx" -H "Content-Type: application/json" \
  -d '{"to":["dksah97@taratps.com"],"title":"결재 요청","message":"출장비 정산 결재가 올라왔습니다.\n확인 부탁드립니다.","link":"https://grow.taratps.com/approval/123"}'
```

- 키에 `notify` 권한(scope)이 있어야 합니다
- `to` 최대 20명, `message` 최대 2000자(일반 텍스트 — 줄바꿈 유지, 태그는 글자 그대로 표시), `link`는 http/https만
- 응답: `{"success":true,"data":{"total":1,"success":1,"failed":0,"results":[{"to":"...","success":true}]}}`
- 오류 코드: `API_KEY_INVALID`(401, 키 없음·오류), `API_KEY_FORBIDDEN`(403, 사용 중지·IP·scope 없음), `NOTIFY_FORBIDDEN`(403, 받는 사람 도메인), `NOTIFY_RATE_LIMIT`(429), `NOTIFY_UNAVAILABLE`(503), `INVALID_INPUT`(400)

## 새 공용 API 를 추가할 때

1. `ApiScopes` 에 scope 상수 추가 (예: `EMPLOYEE = "employee"`) + `ALL` 에 포함
2. 컨트롤러에 `@PreAuthorize("hasAuthority('SCOPE_employee')")`, `SecurityConfig` 경로 규칙에 `hasAuthority("SCOPE_employee")` 추가
3. 키 인증(해시·사용 여부·허용 IP)은 `ApiKeyAuthFilter` 가 자동으로 해 준다 — 사용처는 기존 키에 scope 만 추가 발급(수정 API)하면 바로 쓸 수 있다

## HRM에서 옮길 때

1. 이 서버를 한 번 띄워 `api_client`, `notify_log` 테이블을 만든다(Flyway `V1`, `V2`).
2. 이미 발급한 키를 그대로 쓰려면 `scripts/migrate_from_hrm.sql`로 복사한다(scope 는 `notify` 로 들어간다). 사용처는 주소만 바꾸면 된다.
3. hrm-module-api의 `integration/notify`(발송 API)는 정리 대상(2026-10 기준 이미 삭제됨). `TeamsGraphClient`는 HRM 비밀번호 분실·평가 알림 기능이 쓰므로 HRM에 남긴다.

## HRM과 다른 점

- 관리 API 인증: HRM 로그인(JWT)+ADMIN 역할 → 고정 관리 토큰(`X-Admin-Token`). 공용 서버에는 사용자 DB가 없기 때문.
- 키가 notify 전용(`notify_client`) → 공용(`api_client`) + 권한(scope). 키 인증도 서비스 안 → 보안 필터(`ApiKeyAuthFilter`).
- 새로 발급하는 키 앞머리: `hrmn_` → `tara_` (옛 키도 해시가 같아 그대로 동작)
- 메뉴 권한(`menu_permission`) INSERT 제외
