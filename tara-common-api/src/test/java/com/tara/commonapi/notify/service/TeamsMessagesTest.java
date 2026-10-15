package com.tara.commonapi.notify.service;

import com.tara.commonapi.notify.dto.NotifyDtos.Field;
import com.tara.commonapi.notify.service.TeamsMessages.Spec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Graph·DB 없이 도는 순수 단위 테스트 — Teams 메시지 모양 */
class TeamsMessagesTest {

    private static Spec spec(String type, String icon, String title, String message, List<Field> fields,
                             String link, String linkText, String style) {
        return new Spec("영업관리", type, icon, title, message, null, fields, null, link, linkText, style);
    }

    @Test
    void 기본은_이모지_제목_본문_보낸곳() {
        String html = TeamsMessages.html(spec(null, null, "결재 요청", "줄1\n줄2", null, null, null, null));
        assertThat(html).startsWith("<h3>🔔 결재 요청</h3>");
        assertThat(html).contains("<p>줄1<br>줄2</p>");
        assertThat(html).contains("영업관리 알림");
    }

    @Test
    void 제목이_없으면_사용처_이름이_머리줄() {
        assertThat(TeamsMessages.html(spec(null, null, null, "내용", null, null, null, null)))
                .startsWith("<h3>🔔 영업관리</h3>");
    }

    @Test
    void 종류별_이모지와_직접_지정() {
        assertThat(TeamsMessages.html(spec("urgent", null, "장애", "x", null, null, null, null))).startsWith("<h3>🚨 장애</h3>");
        assertThat(TeamsMessages.html(spec("SUCCESS", null, "완료", "x", null, null, null, null))).startsWith("<h3>✅ 완료</h3>");
        assertThat(TeamsMessages.html(spec("warning", "🔑", "임시 비밀번호", "x", null, null, null, null))).startsWith("<h3>🔑 임시 비밀번호</h3>");
    }

    @Test
    void 기타사항과_링크_문구() {
        String html = TeamsMessages.html(spec(null, null, "결재", "내용",
                List.of(new Field("신청자", "홍길동"), new Field("금액", "125,000원")),
                "https://example.com/?a=1&b=\"2\"", "결재하러 가기", null));
        assertThat(html).contains("<blockquote><b>신청자</b>&nbsp;&nbsp;홍길동<br><b>금액</b>&nbsp;&nbsp;125,000원</blockquote>");
        assertThat(html).contains("<a href=\"https://example.com/?a=1&amp;b=&quot;2&quot;\"><b>결재하러 가기</b></a>");
    }

    @Test
    void 사용처_글자의_HTML_태그는_글자_그대로() {
        String html = TeamsMessages.html(spec(null, null, "제목<b>", "줄1\n<script>x</script>",
                List.of(new Field("<i>라벨</i>", "<img src=x>")), null, null, null));
        assertThat(html).contains("제목&lt;b&gt;");
        assertThat(html).contains("&lt;script&gt;x&lt;/script&gt;");
        assertThat(html).contains("&lt;img src=x&gt;");
        assertThat(html).doesNotContain("<script>").doesNotContain("<img").doesNotContain("<i>");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 카드는_첨부로_보내고_버튼과_표를_넣는다() {
        Map<String, Object> msg = TeamsMessages.chatMessage(spec("urgent", null, "장애 발생", "서버 응답 없음\n\n확인 바랍니다",
                List.of(new Field("시스템", "CRM")), "https://example.com", null, "card"));
        assertThat(msg.get("importance")).isEqualTo("high");
        List<Map<String, Object>> att = (List<Map<String, Object>>) msg.get("attachments");
        assertThat(att).hasSize(1);
        assertThat(att.get(0).get("contentType")).isEqualTo("application/vnd.microsoft.card.adaptive");
        String id = (String) att.get(0).get("id");
        assertThat(((Map<String, Object>) msg.get("body")).get("content")).isEqualTo("<attachment id=\"" + id + "\"></attachment>");

        String json = (String) att.get(0).get("content");
        assertThat(json).contains("\"style\":\"attention\"").contains("\"text\":\"🚨\"").contains("\"text\":\"장애 발생\"")
                .contains("영업관리 알림")
                .contains("\"FactSet\"").contains("\"title\":\"시스템\"")
                .contains("\"Action.OpenUrl\"").contains("\"title\":\"바로가기\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 강조값은_크게_안내문은_작게() {
        Spec s = new Spec("CRM", null, "🔑", "임시 비밀번호", "안현모님, 요청하신 임시 비밀번호입니다.",
                "926YqBjums", null, "본인이 요청하지 않았다면 IT지원팀으로 알려주세요.",
                "https://crm.taratps.com/login", "CRM 로그인", null);
        String html = TeamsMessages.html(s);
        assertThat(html).startsWith("<h3>🔑 임시 비밀번호</h3><p>안현모님, 요청하신 임시 비밀번호입니다.</p>"
                + "<p style=\"font-size:20px\"><b>926YqBjums</b></p>"
                + "<p style=\"color:#888\">본인이 요청하지 않았다면 IT지원팀으로 알려주세요.</p>");

        String json = (String) ((List<Map<String, Object>>) TeamsMessages.chatMessage(
                new Spec("CRM", null, "🔑", "임시 비밀번호", "m", "926YqBjums", null, "n", null, null, "card"))
                .get("attachments")).get(0).get("content");
        assertThat(json).contains("\"style\":\"emphasis\"").contains("\"fontType\":\"Monospace\"").contains("926YqBjums");
    }

    @Test
    void 카드_본문의_마크다운_링크는_끊는다() {
        assertThat(TeamsMessages.plain("[클릭](http://evil)")).isEqualTo("[클릭]​(http://evil)");
    }

    @Test
    void 기본_메시지는_보통_중요도_HTML() {
        Map<String, Object> msg = TeamsMessages.chatMessage(spec(null, null, "t", "m", null, null, null, null));
        assertThat(msg.get("importance")).isEqualTo("normal");
        assertThat(msg).doesNotContainKey("attachments");
    }
}
