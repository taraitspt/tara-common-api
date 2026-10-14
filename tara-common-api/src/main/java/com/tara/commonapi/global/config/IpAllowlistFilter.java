package com.tara.commonapi.global.config;

import com.tara.commonapi.global.exception.ErrorCode;
import com.tara.commonapi.global.util.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 서버 전체 접속 IP 제한 (app.security.ip-allowlist). 비어 있으면 제한 없음.
 * /actuator/health 는 로드밸런서·모니터링용이라 제외한다.
 * SecurityConfig 에서 직접 만들어 보안 필터 체인 맨 앞에 건다(빈으로 등록하지 않음 — 이중 등록 방지).
 */
@Slf4j
public class IpAllowlistFilter extends OncePerRequestFilter {

    private final List<IpAddressMatcher> matchers = new ArrayList<>();
    private final boolean trustProxy;

    public IpAllowlistFilter(String csv, boolean trustProxy) {
        this.trustProxy = trustProxy;
        if (csv != null) {
            for (String s : csv.split("[,\\s]+")) {
                String v = s.trim();
                if (v.isEmpty()) continue;
                try {
                    matchers.add(new IpAddressMatcher(v));
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("APP_IP_ALLOWLIST 에 IP/CIDR 형식이 아닌 값이 있습니다: " + v, e);
                }
            }
        }
        if (matchers.isEmpty()) {
            log.warn("[security] APP_IP_ALLOWLIST 가 비어 있어 모든 IP 에서 접속할 수 있습니다. 운영에서는 반드시 설정하세요.");
        } else {
            log.info("[security] 접속 허용 IP {}개 대역 적용 (trustProxy={})", matchers.size(), trustProxy);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return matchers.isEmpty() || "/actuator/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = ClientIp.of(request, trustProxy);
        for (IpAddressMatcher m : matchers) {
            try {
                if (m.matches(ip)) {
                    chain.doFilter(request, response);
                    return;
                }
            } catch (IllegalArgumentException ignore) {
                // ip 가 이상한 값(위조된 XFF 등)이면 거절 쪽으로
            }
        }
        log.warn("[security] 허용되지 않은 IP 접속 차단 ip={} uri={}", ip, request.getRequestURI());
        JsonError.write(response, ErrorCode.IP_NOT_ALLOWED, ErrorCode.IP_NOT_ALLOWED.getMessage() + " (" + ip + ")");
    }
}
