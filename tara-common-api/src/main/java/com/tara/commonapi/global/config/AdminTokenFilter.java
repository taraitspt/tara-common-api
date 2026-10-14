package com.tara.commonapi.global.config;

import com.tara.commonapi.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 관리 API(/api/admin/**) 인증 — 헤더 X-Admin-Token 이 app.admin.token 과 같으면 ROLE_ADMIN.
 * HRM 은 로그인(JWT)+ADMIN 역할로 막았지만, 공용 API 서버에는 사용자 DB 가 없어 고정 토큰으로 대신한다.
 * 토큰이 설정되지 않았으면 관리 API 는 전부 막힌다(안전한 쪽).
 */
@Slf4j
public class AdminTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Token";

    private final byte[] token;

    public AdminTokenFilter(String token) {
        this.token = token == null || token.isBlank() ? null : token.trim().getBytes(StandardCharsets.UTF_8);
        if (this.token == null) {
            log.warn("[security] APP_ADMIN_TOKEN 이 비어 있어 관리 API(/api/admin/**)를 쓸 수 없습니다.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        if (token == null) {
            JsonError.write(response, ErrorCode.FORBIDDEN, "관리 토큰(APP_ADMIN_TOKEN)이 서버에 설정되어 있지 않습니다.");
            return;
        }
        if (given == null || !MessageDigest.isEqual(token, given.trim().getBytes(StandardCharsets.UTF_8))) {
            log.warn("[security] 관리 토큰 불일치 uri={} ip={}", request.getRequestURI(), request.getRemoteAddr());
            JsonError.write(response, ErrorCode.UNAUTHORIZED, "관리 토큰(" + HEADER + ")이 없거나 올바르지 않습니다.");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        chain.doFilter(request, response);
    }
}
