package com.tara.commonapi.global.config;

import com.tara.commonapi.admin.entity.AdminToken;
import com.tara.commonapi.admin.repository.AdminTokenRepository;
import com.tara.commonapi.client.ApiKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 관리 토큰 인증 — 마스터 / 사람별 토큰 / 처리자 이름 */
class AdminTokenFilterTest {

    private final AdminTokenRepository repo = mock(AdminTokenRepository.class);
    private final AdminTokenFilter filter = new AdminTokenFilter("master-secret", repo);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(String token, String operator) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/me");
        if (token != null) req.addHeader(AdminTokenFilter.HEADER, token);
        if (operator != null) req.addHeader(AdminTokenFilter.OPERATOR_HEADER, operator);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private static boolean has(Authentication a, String role) {
        return a.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(role::equals);
    }

    @Test
    void 마스터_토큰은_MASTER_역할과_처리자_이름() throws Exception {
        call("master-secret", "%ED%99%8D%EA%B8%B8%EB%8F%99");
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        assertThat(a.getName()).isEqualTo("홍길동");
        assertThat(has(a, "ROLE_MASTER")).isTrue();
        assertThat(has(a, "ROLE_ADMIN")).isTrue();
    }

    @Test
    void 사람별_토큰은_토큰의_이름으로_ADMIN만() throws Exception {
        String raw = ApiKeys.newAdminToken();
        AdminToken t = AdminToken.builder().tokenSeq(7L).adminName("김철수").active(true)
                .keyPrefix(raw.substring(0, 12)).keyHash(ApiKeys.sha256(raw)).build();
        when(repo.findByKeyHash(ApiKeys.sha256(raw))).thenReturn(Optional.of(t));

        call(raw, "%EB%8B%A4%EB%A5%B8%EC%82%AC%EB%9E%8C");   // 처리자 헤더는 무시된다
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        assertThat(a.getName()).isEqualTo("김철수");
        assertThat(has(a, "ROLE_ADMIN")).isTrue();
        assertThat(has(a, "ROLE_MASTER")).isFalse();
        verify(repo).touch(eq(7L), any());
    }

    @Test
    void 중지된_토큰은_403() throws Exception {
        String raw = ApiKeys.newAdminToken();
        AdminToken t = AdminToken.builder().tokenSeq(8L).adminName("이영희").active(false)
                .keyPrefix(raw.substring(0, 12)).keyHash(ApiKeys.sha256(raw)).build();
        when(repo.findByKeyHash(anyString())).thenReturn(Optional.of(t));

        assertThat(call(raw, null).getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 틀린_토큰과_API_키는_401() throws Exception {
        when(repo.findByKeyHash(anyString())).thenReturn(Optional.empty());
        assertThat(call("tadm_wrong", null).getStatus()).isEqualTo(401);
        assertThat(call("tara_api_key_is_not_admin", null).getStatus()).isEqualTo(401);
        assertThat(call(null, null).getStatus()).isEqualTo(401);
    }

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
