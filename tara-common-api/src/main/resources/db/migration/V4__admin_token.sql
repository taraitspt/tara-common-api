-- 사람별 관리 토큰 — 관리 화면(/admin.html)에 들어가는 열쇠를 사람마다 따로 발급한다.
-- 서버 환경변수 APP_ADMIN_TOKEN 은 '마스터 토큰'으로 남는다: 관리 토큰 발급·중지는 마스터만 하고,
-- 관리 토큰이 전부 막혔을 때 복구 수단이 된다.
-- 토큰 원문은 발급 응답에서 한 번만 보여 주고 DB 에는 SHA-256 해시만 둔다(API 키와 같은 방식, 앞머리 tadm_).

CREATE TABLE admin_token (
    token_seq      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '순번',
    admin_name     VARCHAR(20)  NOT NULL COMMENT '관리자 이름 — 감사 컬럼·발급 이력의 처리자로 남는다',
    key_prefix     VARCHAR(16)  NOT NULL COMMENT '토큰 앞부분 — 목록에서 알아보는 용도',
    key_hash       CHAR(64)     NOT NULL COMMENT '토큰 SHA-256 (hex)',
    is_active      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '사용 여부 — 끄면 그 토큰으로 못 들어온다',
    memo           VARCHAR(500) NULL,
    last_used_at   DATETIME     NULL COMMENT '마지막 사용 시각',
    created_at     DATETIME     NULL,
    updated_at     DATETIME     NULL,
    created_id     VARCHAR(20)  NULL,
    updated_id     VARCHAR(20)  NULL,
    PRIMARY KEY (token_seq),
    UNIQUE KEY uk_admin_token_key_hash (key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='관리 화면 토큰 (사람별)';
