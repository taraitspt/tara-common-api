package com.tara.commonapi.global.config;

import com.tara.commonapi.client.ApiKeys;
import com.tara.commonapi.client.ApiScopes;
import com.tara.commonapi.client.entity.ApiClient;
import com.tara.commonapi.client.repository.ApiClientRepository;
import com.tara.commonapi.global.exception.ErrorCode;
import com.tara.commonapi.global.util.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 공용 API 키 인증 — /api/** (관리 API 제외)는 헤더 X-Api-Key 필수.
 * 키 확인(해시) → 사용 여부 → 사용처 허용 IP 순으로 검사하고, 통과하면
 * 사용처(ApiClient)를 principal 로, scope 를 SCOPE_* 권한으로 올린다.
 * 어떤 API 를 쓸 수 있는지는 SecurityConfig 경로 규칙과 각 컨트롤러의 @PreAuthorize 가 scope 로 검사한다.
 */
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

    private final ApiClientRepository clients;
    private final boolean trustProxy;

    public ApiKeyAuthFilter(ApiClientRepository clients, boolean trustProxy) {
        this.clients = clients;
        this.trustProxy = trustProxy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = ClientIp.of(request, trustProxy);
        String key = request.getHeader(HEADER);
        if (!StringUtils.hasText(key)) {
            JsonError.write(response, ErrorCode.API_KEY_INVALID, "API 키 헤더(" + HEADER + ")가 없습니다.");
            return;
        }
        Optional<ApiClient> found = clients.findByKeyHash(ApiKeys.sha256(key.trim()));
        if (found.isEmpty()) {
            log.warn("[security] 잘못된 API 키 ip={} uri={}", ip, request.getRequestURI());
            JsonError.write(response, ErrorCode.API_KEY_INVALID, ErrorCode.API_KEY_INVALID.getMessage());
            return;
        }
        ApiClient c = found.get();
        if (!Boolean.TRUE.equals(c.getActive())) {
            JsonError.write(response, ErrorCode.API_KEY_FORBIDDEN, "사용 중지된 키입니다. 공용 API 관리자에게 문의하세요.");
            return;
        }
        if (StringUtils.hasText(c.getAllowedIps()) && !ipAllowed(c.getAllowedIps(), ip)) {
            log.warn("[security] 허용되지 않은 IP client={} ip={}", c.getClientName(), ip);
            JsonError.write(response, ErrorCode.API_KEY_FORBIDDEN, "이 키에 허용되지 않은 IP 에서 호출했습니다: " + ip);
            return;
        }
        clients.touch(c.getClientSeq(), LocalDateTime.now());
        List<GrantedAuthority> authorities = c.scopeSet().stream()
                .map(s -> (GrantedAuthority) new SimpleGrantedAuthority(ApiScopes.AUTHORITY_PREFIX + s))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(c, null, authorities));
        chain.doFilter(request, response);
    }

    static boolean ipAllowed(String csv, String ip) {
        if (ip == null) return false;
        for (String s : csv.split(",")) {
            String cidr = s.trim();
            if (cidr.isEmpty()) continue;
            try {
                if (new IpAddressMatcher(cidr).matches(ip)) return true;
            } catch (IllegalArgumentException ignore) {
                // 저장 때 검사하지만, 혹시 잘못 들어간 값은 건너뛴다(허용으로 치지 않음)
            }
        }
        return false;
    }
}
