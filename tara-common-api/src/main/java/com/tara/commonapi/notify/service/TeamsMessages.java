package com.tara.commonapi.notify.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tara.commonapi.notify.dto.NotifyDtos.Field;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Teams 메시지(Graph chatMessage 본문) 만들기 — 발송 API 의 표준 알림 틀을 한 곳에서 정한다.
 *
 * 틀: [이모지 제목] → 본문 → [강조값] → 기타사항(라벨·값) → 안내문 → [링크 버튼] → 보낸 곳
 * - message(기본): HTML. Teams 가 CSS 를 대부분 지우므로 제목(h3)·굵기·글자 크기·인용 블록으로 꾸민다.
 *   Teams 알림 미리보기·채팅 목록에 제목과 내용이 그대로 보인다.
 * - card: Adaptive Card 1.4. 종류별 색 머리 → 강조 박스 → 정보 표 → 색 있는 버튼.
 *   보기엔 가장 좋지만 알림 미리보기에는 '카드'로만 보인다.
 *
 * 사용처가 보낸 글자는 전부 일반 텍스트로 다룬다 — HTML 은 특수문자를 바꾸고,
 * 카드의 마크다운 링크 문법([글](주소))은 끊어서 링크·태그를 몰래 심지 못하게 한다.
 */
public final class TeamsMessages {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TeamsMessages() {}

    /** 알림 종류 — 기본 이모지, 카드 색, Teams 중요도 */
    public enum Type {
        INFO("🔔", "accent", "normal"),
        SUCCESS("✅", "good", "normal"),
        WARNING("⚠️", "warning", "normal"),
        URGENT("🚨", "attention", "high");

        final String icon;
        final String cardStyle;
        final String importance;

        Type(String icon, String cardStyle, String importance) {
            this.icon = icon;
            this.cardStyle = cardStyle;
            this.importance = importance;
        }

        static Type of(String s) {
            return StringUtils.hasText(s) ? valueOf(s.trim().toUpperCase(Locale.ROOT)) : INFO;
        }
    }

    /** 메시지 재료 — 값 검증은 DTO·서비스에서 끝났다고 본다 */
    public record Spec(String clientName, String type, String icon, String title, String message,
                       String highlight, List<Field> fields, String note,
                       String link, String linkText, String style) {

        Type kind() { return Type.of(type); }

        String emoji() { return StringUtils.hasText(icon) ? icon.trim() : kind().icon; }

        /** 제목이 없으면 사용처 이름을 머리줄로 쓴다 */
        String heading() { return StringUtils.hasText(title) ? title.trim() : clientName; }

        String buttonText() { return StringUtils.hasText(linkText) ? linkText.trim() : "바로가기"; }

        List<Field> fieldList() { return fields == null ? List.of() : fields; }

        boolean card() { return "card".equalsIgnoreCase(style == null ? "" : style.trim()); }
    }

    /** Graph POST /chats/{id}/messages 본문 */
    public static Map<String, Object> chatMessage(Spec s) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("importance", s.kind().importance);
        if (s.card()) {
            String id = UUID.randomUUID().toString().replace("-", "");
            body.put("body", Map.of("contentType", "html", "content", "<attachment id=\"" + id + "\"></attachment>"));
            body.put("attachments", List.of(Map.of(
                    "id", id,
                    "contentType", "application/vnd.microsoft.card.adaptive",
                    "content", toJson(card(s)))));
        } else {
            body.put("body", Map.of("contentType", "html", "content", html(s)));
        }
        return body;
    }

    /* ───────── message (HTML) ───────── */

    static String html(Spec s) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>").append(esc(s.emoji())).append(' ').append(esc(s.heading())).append("</h3>");
        sb.append("<p>").append(lines(s.message())).append("</p>");
        if (StringUtils.hasText(s.highlight())) {
            sb.append("<p style=\"font-size:20px\"><b>").append(esc(s.highlight().trim())).append("</b></p>");
        }
        if (!s.fieldList().isEmpty()) {
            sb.append("<blockquote>");
            for (int i = 0; i < s.fieldList().size(); i++) {
                Field f = s.fieldList().get(i);
                if (i > 0) sb.append("<br>");
                sb.append("<b>").append(esc(f.label().trim())).append("</b>&nbsp;&nbsp;").append(lines(f.value()));
            }
            sb.append("</blockquote>");
        }
        if (StringUtils.hasText(s.note())) {
            sb.append("<p style=\"color:#888\">").append(lines(s.note())).append("</p>");
        }
        if (s.link() != null) {
            sb.append("<p>👉 <a href=\"").append(esc(s.link())).append("\"><b>").append(esc(s.buttonText())).append("</b></a></p>");
        }
        sb.append("<p style=\"color:#888;font-size:12px\">").append(esc(s.clientName())).append(" 알림</p>");
        return sb.toString();
    }

    private static String lines(String text) {
        return esc(text.trim()).replace("\r\n", "\n").replace("\n", "<br>");
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /* ───────── card (Adaptive Card) ───────── */

    static Map<String, Object> card(Spec s) {
        List<Object> body = new ArrayList<>();

        // 머리 — 종류별 색 띠 안에 [큰 이모지 | 제목 + 보낸 곳]
        body.add(Map.of(
                "type", "Container", "style", s.kind().cardStyle, "bleed", true,
                "items", List.of(Map.of(
                        "type", "ColumnSet",
                        "columns", List.of(
                                Map.of("type", "Column", "width", "auto", "verticalContentAlignment", "Center",
                                        "items", List.of(text(s.emoji(), Map.of("size", "ExtraLarge")))),
                                Map.of("type", "Column", "width", "stretch", "verticalContentAlignment", "Center",
                                        "items", List.of(
                                                text(s.heading(), Map.of("size", "Large", "weight", "Bolder")),
                                                text(s.clientName() + " 알림", Map.of("size", "Small", "isSubtle", true, "spacing", "None")))))))));

        // 본문 — 줄마다 TextBlock(클라이언트마다 한 블록 안 \n 처리가 달라서), 빈 줄은 간격으로
        boolean first = true, gap = false;
        for (String line : s.message().trim().replace("\r\n", "\n").split("\n", -1)) {
            if (line.isBlank()) { gap = true; continue; }
            body.add(text(line, Map.of("spacing", first ? "Medium" : gap ? "Medium" : "None")));
            first = false;
            gap = false;
        }

        // 강조값 — 회색 상자 가운데 크게
        if (StringUtils.hasText(s.highlight())) {
            body.add(Map.of(
                    "type", "Container", "style", "emphasis", "spacing", "Medium",
                    "items", List.of(text(s.highlight().trim(), Map.of(
                            "size", "ExtraLarge", "weight", "Bolder", "fontType", "Monospace",
                            "horizontalAlignment", "Center")))));
        }

        if (!s.fieldList().isEmpty()) {
            List<Object> facts = new ArrayList<>();
            for (Field f : s.fieldList()) facts.add(Map.of("title", plain(f.label().trim()), "value", plain(f.value().trim())));
            body.add(Map.of("type", "FactSet", "spacing", "Medium", "separator", true, "facts", facts));
        }

        if (StringUtils.hasText(s.note())) {
            body.add(text(s.note().trim(), Map.of("size", "Small", "isSubtle", true, "spacing", "Medium")));
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        card.put("body", body);
        if (s.link() != null) {
            card.put("actions", List.of(Map.of(
                    "type", "Action.OpenUrl", "title", s.buttonText(), "url", s.link(), "style", "positive")));
        }
        card.put("msteams", Map.of("width", "Full"));
        return card;
    }

    private static Map<String, Object> text(String value, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "TextBlock");
        m.put("text", plain(value));
        m.put("wrap", true);
        m.putAll(extra);
        return m;
    }

    /** 카드 마크다운의 링크 문법만 끊는다 — 굵게 같은 꾸밈은 남겨도 해가 없다 */
    static String plain(String s) {
        return s.replace("](", "]​(");
    }

    private static String toJson(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
