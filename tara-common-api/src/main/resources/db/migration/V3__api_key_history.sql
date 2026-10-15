-- API 키 발급 이력 — 누구에게 언제 발급(재발급)했는지, 중지·재개·수정을 누가 했는지 남긴다.
-- api_client 는 현재 상태 한 줄뿐이라 재발급하면 이전 키 정보가 덮어써진다. 이 테이블은 지우지 않고 쌓기만 한다.
-- 처리자(actor)는 관리 화면에 입력한 이름(헤더 X-Admin-Operator) — 관리 토큰은 하나라서 사람을 따로 받는다.

CREATE TABLE api_key_history (
    history_seq    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '순번',
    client_seq     BIGINT       NOT NULL COMMENT '사용처',
    action         VARCHAR(20)  NOT NULL COMMENT 'ISSUE 발급 / REISSUE 재발급 / STOP 중지 / RESUME 재개 / UPDATE 수정',
    key_prefix     VARCHAR(16)  NULL COMMENT '그때의 키 앞부분 — 어떤 키였는지 알아보는 용도',
    issued_to      VARCHAR(100) NULL COMMENT '키를 전달받은 사람 (발급·재발급)',
    detail         VARCHAR(500) NULL COMMENT '사유 또는 변경 내용',
    actor          VARCHAR(50)  NOT NULL COMMENT '처리자',
    created_at     DATETIME     NOT NULL,
    PRIMARY KEY (history_seq),
    KEY idx_api_key_history_client (client_seq, history_seq),
    CONSTRAINT fk_api_key_history_client FOREIGN KEY (client_seq) REFERENCES api_client (client_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API 키 발급·변경 이력';

-- 이미 발급된 키는 등록 시점 기준 '발급' 한 줄로 채운다(받은 사람은 담당자로 본다).
INSERT INTO api_key_history (client_seq, action, key_prefix, issued_to, detail, actor, created_at)
SELECT client_seq, 'ISSUE', key_prefix, owner_name, '이력 기능 도입 전 발급분', COALESCE(created_id, 'system'),
       COALESCE(created_at, NOW())
  FROM api_client;
