package com.tara.commonapi.notify.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** DB·MS Graph 없이 도는 순수 단위 테스트 — 메시지 변환 */
class NotifyServiceTest {

    @Test
    void 본문의_HTML_태그는_글자_그대로_보낸다() {
        String html = NotifyService.toHtml("영업관리", "제목<b>", "줄1\n<script>x</script>", null);
        assertThat(html).contains("제목&lt;b&gt;");
        assertThat(html).contains("줄1<br>&lt;script&gt;x&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>");
    }

    @Test
    void 링크가_있으면_바로가기를_붙인다() {
        String html = NotifyService.toHtml("영업관리", null, "내용", "https://example.com/?a=1&b=\"2\"");
        assertThat(html).contains("<a href=\"https://example.com/?a=1&amp;b=&quot;2&quot;\">바로가기</a>");
    }
}
