package com.tara.commonapi.notify.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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
            String link,
            /** 알림 종류 — info(🔔, 기본) / success(✅) / warning(⚠️) / urgent(🚨, Teams '중요' 표시) */
            @Pattern(regexp = "(?i)info|success|warning|urgent", message = "type 은 info, success, warning, urgent 중 하나입니다.")
            String type,
            /** 제목 앞 이모지를 직접 지정(선택) — 예: 🔑. 비우면 type 의 기본 이모지 */
            @Size(max = 8, message = "icon 은 이모지 하나(8자)까지입니다.")
            String icon,
            /** 기타사항 — 라벨·값 표(선택, 10줄까지). 예: 신청자 / 홍길동 */
            @Size(max = 10, message = "fields 는 10개까지입니다.")
            List<@Valid Field> fields,
            /** 링크 버튼 문구(선택). 비우면 "바로가기" */
            @Size(max = 30, message = "linkText 는 30자까지입니다.")
            String linkText,
            /** 모양 — message(기본, 알림 미리보기에 내용이 보임) / card(Adaptive Card — 색 띠·표·버튼) */
            @Pattern(regexp = "(?i)message|card", message = "style 은 message 또는 card 입니다.")
            String style,
            /** 크게 강조할 값(선택) — 임시 비밀번호·인증번호·금액 등. 본문 아래에 크게 보인다 */
            @Size(max = 100, message = "highlight 는 100자까지입니다.")
            String highlight,
            /** 맨 아래 작은 안내문(선택) — 예: 본인이 요청하지 않았다면 IT지원팀으로 알려주세요 */
            @Size(max = 500, message = "note 는 500자까지입니다.")
            String note
    ) {
        /** 예전 호출 모양(to·title·message·link) 그대로 — 테스트·내부용 */
        public SendRequest(List<String> to, String title, String message, String link) {
            this(to, title, message, link, null, null, null, null, null, null, null);
        }
    }

    public record Field(
            @NotBlank(message = "fields 의 label 이 비어 있습니다.") @Size(max = 50, message = "fields 의 label 은 50자까지입니다.")
            String label,
            @NotBlank(message = "fields 의 value 가 비어 있습니다.") @Size(max = 300, message = "fields 의 value 는 300자까지입니다.")
            String value
    ) {}

    public record SendResult(int total, int success, int failed, List<Recipient> results) {}

    public record Recipient(String to, boolean success, String error) {}

    /* ───────── 관리 화면(발송 기록) ───────── */

    public record LogItem(
            Long logSeq, String recipient, String title, Integer messageLen, Boolean success,
            String errorMessage, String requestIp, LocalDateTime sentAt
    ) {}
}
