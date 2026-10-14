package com.tara.commonapi.notify.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Teams 발송 API 기록 — 받는 사람 한 명당 한 줄. 본문은 저장하지 않고 길이만. */
@Entity
@Table(name = "notify_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NotifyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_seq")
    private Long logSeq;

    @Column(name = "client_seq", nullable = false)
    private Long clientSeq;

    @Column(name = "recipient", nullable = false, length = 200)
    private String recipient;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "message_len", nullable = false)
    private Integer messageLen;

    @Column(name = "is_success", nullable = false)
    private Boolean success;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "request_ip", length = 45)
    private String requestIp;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
