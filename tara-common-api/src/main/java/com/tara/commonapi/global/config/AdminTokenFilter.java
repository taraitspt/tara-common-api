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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 관리 API(/api/admin/**) 인증 — 헤더 X-Admin-Token 이 app.admin.token 과 같으면 ROLE_ADMIN.
 * 토큰은 하나라 사람을 구분하지 못하므로, 처리자 이름은 X-Admin-Operator 로 따로 받아 주체 이름으로 쓴다.
 * HRM 은 로그인(JWT)+ADMIN 역할로 막았지만, 공용 API 서버에는 사용자 DB 가 없어 고정 토큰으로 대신한다.
 * 토큰이 설정되지 않았으면 관리 API 는 전부 막힌다(안전한 쪽).
 */
@Slf4j
public class AdminTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Token";
    public static final String OPERATOR_HEADER = "X-Admin-Operator";

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
                operator(request.getHeader(OPERATOR_HEADER)), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        chain.doFilter(request, response);
    }

    /**
     * 처리자 이름 — 관리 화면이 X-Admin-Operator 에 URL 인코딩해서 보낸다(한글은 헤더에 그대로 못 싣는다).
     * 감사 컬럼(created_id/updated_id, 20자)과 발급 이력에 남는다. 없거나 깨졌으면 "admin".
     */
    static String operator(String raw) {
        if (raw == null || raw.isBlank()) return "admin";
        String name;
        try {
            name = URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "admin";
        }
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) return "admin";
        return name.length() > 20 ? name.substring(0, 20) : name;
    }
}
