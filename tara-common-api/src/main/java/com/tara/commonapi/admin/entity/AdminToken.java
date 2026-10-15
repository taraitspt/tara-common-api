package com.tara.commonapi.admin.entity;

import com.tara.commonapi.global.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** 사람별 관리 토큰 — 해시만 저장. 마스터 토큰(APP_ADMIN_TOKEN)은 DB 에 없다. */
@Entity
@Table(name = "admin_token")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_seq")
    private Long tokenSeq;

    /** 관리자 이름 — 감사 컬럼(20자)에 그대로 들어가므로 20자까지 */
    @Column(name = "admin_name", nullable = false, length = 20)
    private String adminName;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
