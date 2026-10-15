package com.tara.commonapi.global.config;

import com.tara.commonapi.admin.entity.AdminToken;
import com.tara.commonapi.admin.repository.AdminTokenRepository;
import com.tara.commonapi.client.ApiKeys;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

/**
 * 관리 API(/api/admin/**) 인증 — 헤더 X-Admin-Token.
 * - 마스터 토큰(app.admin.token, 서버 환경변수): ROLE_ADMIN + ROLE_MASTER. 관리 토큰 발급·중지는 마스터만.
 *   한 사람에게 묶이지 않으므로 처리자 이름은 X-Admin-Operator 로 따로 받는다.
 * - 사람별 관리 토큰(admin_token 테이블, 관리 화면에서 마스터가 발급): ROLE_ADMIN. 처리자는 토큰의 이름.
 * HRM 은 로그인(JWT)+ADMIN 역할로 막았지만, 공용 API 서버에는 사용자 DB 가 없어 토큰으로 대신한다.
 */
@Slf4j
public class AdminTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Token";
    public static final String OPERATOR_HEADER = "X-Admin-Operator";

    private final byte[] master;
    private final AdminTokenRepository tokens;

    public AdminTokenFilter(String master, AdminTokenRepository tokens) {
        this.master = master == null || master.isBlank() ? null : master.trim().getBytes(StandardCharsets.UTF_8);
        this.tokens = tokens;
        if (this.master == null) {
            log.warn("[security] APP_ADMIN_TOKEN(마스터 토큰)이 비어 있습니다. 관리 토큰 발급·복구를 할 수 없습니다.");
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
        given = given == null ? "" : given.trim();

        if (master != null && MessageDigest.isEqual(master, given.getBytes(StandardCharsets.UTF_8))) {
            authenticate(operator(request.getHeader(OPERATOR_HEADER)), "ROLE_ADMIN", "ROLE_MASTER");
            chain.doFilter(request, response);
            return;
        }

        Optional<AdminToken> found = given.startsWith(ApiKeys.ADMIN_HEAD)
                ? tokens.findByKeyHash(ApiKeys.sha256(given)) : Optional.empty();
        if (found.isEmpty()) {
            log.warn("[security] 관리 토큰 불일치 uri={} ip={}", request.getRequestURI(), request.getRemoteAddr());
            JsonError.write(response, ErrorCode.UNAUTHORIZED, "관리 토큰(" + HEADER + ")이 없거나 올바르지 않습니다.");
            return;
        }
        AdminToken t = found.get();
        if (!Boolean.TRUE.equals(t.getActive())) {
            log.warn("[security] 중지된 관리 토큰 #{} {} ip={}", t.getTokenSeq(), t.getAdminName(), request.getRemoteAddr());
            JsonError.write(response, ErrorCode.FORBIDDEN, "사용 중지된 관리 토큰입니다. 마스터 관리자에게 문의하세요.");
            return;
        }
        tokens.touch(t.getTokenSeq(), LocalDateTime.now());
        authenticate(t.getAdminName(), "ROLE_ADMIN");
        chain.doFilter(request, response);
    }

    private static void authenticate(String name, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                name, null, Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }

    /**
     * 마스터 토큰으로 들어올 때의 처리자 이름 — 관리 화면이 X-Admin-Operator 에 URL 인코딩해서 보낸다
     * (한글은 헤더에 그대로 못 싣는다). 감사 컬럼(created_id/updated_id, 20자)과 발급 이력에 남는다.
     * 없거나 깨졌으면 "admin".
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
