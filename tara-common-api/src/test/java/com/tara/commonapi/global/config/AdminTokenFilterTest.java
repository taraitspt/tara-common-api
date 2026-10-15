package com.tara.commonapi.global.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 처리자 이름(X-Admin-Operator) 해석 */
class AdminTokenFilterTest {

    @Test
    void 한글_이름은_URL_인코딩으로_받는다() {
        assertThat(AdminTokenFilter.operator("%ED%99%8D%EA%B8%B8%EB%8F%99")).isEqualTo("홍길동");
    }

    @Test
    void 없거나_깨졌으면_admin() {
        assertThat(AdminTokenFilter.operator(null)).isEqualTo("admin");
        assertThat(AdminTokenFilter.operator("  ")).isEqualTo("admin");
        assertThat(AdminTokenFilter.operator("%E0%A4%A")).isEqualTo("admin");
    }

    @Test
    void 감사_컬럼_길이에_맞춰_20자로_자른다() {
        assertThat(AdminTokenFilter.operator("a".repeat(30))).hasSize(20);
    }
}
