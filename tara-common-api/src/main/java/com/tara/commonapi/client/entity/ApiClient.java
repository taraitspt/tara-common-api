package com.tara.commonapi.client.entity;

import com.tara.commonapi.client.ApiScopes;
import com.tara.commonapi.global.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 공용 API 사용처 — 부서(시스템)마다 하나, API 키는 해시만 저장.
 * 어떤 API 를 쓸 수 있는지는 scopes 로 정한다(ApiScopes).
 */
@Entity
@Table(name = "api_client")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ApiClient extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_seq")
    private Long clientSeq;

    @Column(name = "client_name", nullable = false, length = 100)
    private String clientName;

    @Column(name = "owner_dept", length = 100)
    private String ownerDept;

    @Column(name = "owner_name", length = 50)
    private String ownerName;

    /** 키 앞부분 — 목록에서 알아보는 용도 */
    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    /** API 키 SHA-256 hex */
    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    /** 쓸 수 있는 API(쉼표) — ApiScopes 참고. 예: "notify" */
    @Column(name = "scopes", nullable = false, length = 200)
    private String scopes;

    /** 허용 IP/CIDR 쉼표 목록, 비면 제한 없음 */
    @Column(name = "allowed_ips", length = 500)
    private String allowedIps;

    @Column(name = "rate_per_min", nullable = false)
    private Integer ratePerMin;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    public Set<String> scopeSet() {
        return ApiScopes.parse(scopes);
    }

    /** 인증 주체(principal)로 쓰여서 감사·로그에 getName()/toString() 이 찍힌다 — 짧게 */
    @Override
    public String toString() {
        return "api:" + clientSeq;
    }
}
