-- (선택) HRM 에서 이미 발급한 API 키·기록을 tara_common 으로 옮긴다.
-- 키는 해시로만 저장돼 있으므로 그대로 복사하면 사용처는 키를 바꾸지 않고 주소만 바꾸면 된다.
-- 두 DB 가 같은 MariaDB 서버에 있을 때 기준. tara-common-api 를 한 번 띄워 V1·V2 테이블이 생긴 뒤 실행한다.
-- HRM 키는 전부 Teams 발송용이므로 scopes 는 'notify' 로 넣는다.

INSERT INTO tara_common.api_client
       (client_seq, client_name, owner_dept, owner_name, key_prefix, key_hash, scopes, allowed_ips,
        rate_per_min, is_active, memo, last_used_at, created_at, updated_at, created_id, updated_id)
SELECT client_seq, client_name, owner_dept, owner_name, key_prefix, key_hash, 'notify', allowed_ips,
       rate_per_min, is_active, memo, last_used_at, created_at, updated_at, created_id, updated_id
  FROM hrm_module.notify_client;

INSERT INTO tara_common.notify_log
       (log_seq, client_seq, recipient, title, message_len, is_success, error_message, request_ip, sent_at)
SELECT log_seq, client_seq, recipient, title, message_len, is_success, error_message, request_ip, sent_at
  FROM hrm_module.notify_log;
