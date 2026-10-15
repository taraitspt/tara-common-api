package com.tara.commonapi.client.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** API 키 발급·변경 이력 — 쌓기만 하고 고치거나 지우지 않는다. */
@Entity
@Table(name = "api_key_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ApiKeyHistory {

    public static final String ISSUE = "ISSUE";
    public static final String REISSUE = "REISSUE";
    public static final String STOP = "STOP";
    public static final String RESUME = "RESUME";
    public static final String UPDATE = "UPDATE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_seq")
    private Long historySeq;

    @Column(name = "client_seq", nullable = false)
    private Long clientSeq;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "key_prefix", length = 16)
    private String keyPrefix;

    /** 키를 전달받은 사람 (발급·재발급) */
    @Column(name = "issued_to", length = 100)
    private String issuedTo;

    /** 사유 또는 변경 내용 */
    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "actor", nullable = false, length = 50)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
