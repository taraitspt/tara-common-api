-- 키 체계를 공용으로 — notify 전용 사용처(notify_client)를 서버 공용 사용처(api_client)로 바꾸고,
-- 키마다 쓸 수 있는 API(scopes)를 적는다. 기존 키는 전부 'notify' 권한을 그대로 가진다(사용처 영향 없음).
-- notify_log 의 FK 는 RENAME TABLE 이 자동으로 따라온다.

RENAME TABLE notify_client TO api_client;

ALTER TABLE api_client
    ADD COLUMN scopes VARCHAR(200) NOT NULL DEFAULT 'notify'
        COMMENT '쓸 수 있는 API(쉼표). 예: notify — 코드의 ApiScopes 참고'
        AFTER key_hash;

ALTER TABLE api_client COMMENT = '공용 API 사용처 (API 키·권한)';
