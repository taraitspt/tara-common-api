-- Teams 발송 API — hrm-module-api V32__notify_api.sql 에서 옮겨 왔다(메뉴 권한 INSERT 는 HRM 전용이라 뺐다).
-- 다른 부서 시스템이 이 서버를 통해 Teams 1:1 메시지를 보낸다.
-- 서비스 계정(MS_USERNAME/MS_PASSWORD)은 이 서버에만 두고, 부서(시스템)마다 API 키를 발급한다.
-- 키 원문은 발급 응답에서 한 번만 보여 주고 DB 에는 SHA-256 해시만 둔다.

CREATE TABLE notify_client (
    client_seq     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '순번',
    client_name    VARCHAR(100) NOT NULL COMMENT '사용처 이름 (예: 영업관리 GROW)',
    owner_dept     VARCHAR(100) NULL COMMENT '담당 부서',
    owner_name     VARCHAR(50)  NULL COMMENT '담당자',
    key_prefix     VARCHAR(16)  NOT NULL COMMENT '키 앞부분 — 목록에서 어떤 키인지 알아보는 용도',
    key_hash       CHAR(64)     NOT NULL COMMENT 'API 키 SHA-256 (hex)',
    allowed_ips    VARCHAR(500) NULL COMMENT '호출 허용 IP/CIDR 쉼표 목록. 비면 IP 제한 없음',
    rate_per_min   INT          NOT NULL DEFAULT 30 COMMENT '1분당 최대 발송 건수(받는 사람 기준)',
    is_active      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '사용 여부 — 끄면 그 키로는 못 보낸다',
    memo           VARCHAR(500) NULL,
    last_used_at   DATETIME     NULL COMMENT '마지막 호출 시각',
    created_at     DATETIME     NULL,
    updated_at     DATETIME     NULL,
    created_id     VARCHAR(20)  NULL,
    updated_id     VARCHAR(20)  NULL,
    PRIMARY KEY (client_seq),
    UNIQUE KEY uk_notify_client_key_hash (key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Teams 발송 API 사용처 (API 키)';

CREATE TABLE notify_log (
    log_seq        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '순번',
    client_seq     BIGINT       NOT NULL COMMENT '보낸 사용처',
    recipient      VARCHAR(200) NOT NULL COMMENT '받는 사람 이메일',
    title          VARCHAR(200) NULL,
    message_len    INT          NOT NULL DEFAULT 0 COMMENT '본문 길이 — 본문 자체는 저장하지 않는다',
    is_success     TINYINT(1)   NOT NULL,
    error_message  VARCHAR(500) NULL,
    request_ip     VARCHAR(45)  NULL COMMENT '호출한 서버 IP',
    sent_at        DATETIME     NOT NULL,
    PRIMARY KEY (log_seq),
    KEY idx_notify_log_client (client_seq, sent_at),
    CONSTRAINT fk_notify_log_client FOREIGN KEY (client_seq) REFERENCES notify_client (client_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Teams 발송 API 기록';
