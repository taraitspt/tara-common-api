package com.tara.commonapi.notify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Teams 발송 API 요청·응답 DTO. 사용처 관리 DTO 는 client 패키지(ClientDtos)에 있다. */
public final class NotifyDtos {

    private NotifyDtos() {}

    /* ───────── 외부 시스템이 부르는 발송 API ───────── */

    /**
     * POST /api/notify/teams (헤더 X-Api-Key).
     * message 는 일반 텍스트 — 서버가 HTML 로 바꿔 보낸다(줄바꿈 유지, 태그는 글자 그대로 보인다).
     */
    public record SendRequest(
            @NotEmpty(message = "받는 사람(to)을 한 명 이상 넣어 주세요.")
            @Size(max = 20, message = "받는 사람은 한 번에 20명까지입니다.")
            List<@NotBlank(message = "받는 사람 이메일이 비어 있습니다.") String> to,
            @Size(max = 100, message = "제목은 100자까지입니다.")
            String title,
            @NotBlank(message = "보낼 내용(message)이 비어 있습니다.")
            @Size(max = 2000, message = "내용은 2000자까지입니다.")
            String message,
            /** 메시지 아래에 붙는 바로가기 링크(선택, http/https 만) */
            @Size(max = 500, message = "링크는 500자까지입니다.")
            String link
    ) {}

    public record SendResult(int total, int success, int failed, List<Recipient> results) {}

    public record Recipient(String to, boolean success, String error) {}

    /* ───────── 관리 화면(발송 기록) ───────── */

    public record LogItem(
            Long logSeq, String recipient, String title, Integer messageLen, Boolean success,
            String errorMessage, String requestIp, LocalDateTime sentAt
    ) {}
}
