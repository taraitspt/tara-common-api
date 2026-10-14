package com.tara.commonapi.global.config;

import com.tara.commonapi.client.repository.ApiClientRepository;
import com.tara.commonapi.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * 공용 API 보안 설정.
 * - /api/admin/**   : 관리 토큰(X-Admin-Token) → ROLE_ADMIN (AdminTokenFilter).
 * - 그 외 /api/**   : API 키(X-Api-Key) → 사용처 + SCOPE_* 권한 (ApiKeyAuthFilter).
 *                     어떤 API 를 쓸 수 있는지는 키의 scope 로 정한다(ApiScopes).
 * - /actuator/health, Swagger(켜져 있을 때) : permitAll.
 * - 나머지는 전부 거절.
 * 서버 전체 IP 제한(IpAllowlistFilter)은 위 규칙보다 먼저 걸린다.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.security.ip-allowlist:}")
    private String ipAllowlist;
    @Value("${app.security.trust-proxy:false}")
    private boolean trustProxy;
    @Value("${app.admin.token:}")
    private String adminToken;
    @Value("${app.swagger.enabled:false}")
    private boolean swaggerEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ApiClientRepository apiClients) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))   // h2-console(local)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new IpAllowlistFilter(ipAllowlist, trustProxy), SecurityContextHolderFilter.class)
                .addFilterBefore(new AdminTokenFilter(adminToken), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new ApiKeyAuthFilter(apiClients, trustProxy), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                JsonError.write(res, ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage()))
                        // 키는 맞지만 그 API 의 scope 가 없는 경우가 대부분 — 메시지로 바로 알려준다
                        .accessDeniedHandler((req, res, ex) ->
                                JsonError.write(res, ErrorCode.API_KEY_FORBIDDEN,
                                        "이 키로 쓸 수 없는 API 입니다. 키의 권한(scope)을 확인하세요.")))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/error").permitAll();
                    // 관리 화면(정적 HTML). 화면 접근 자체는 nginx 가 관리자 IP 로 제한하고,
                    // 화면이 부르는 /api/admin/** 은 어차피 관리 토큰이 있어야 동작한다.
                    auth.requestMatchers("/admin.html").permitAll();
                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    auth.requestMatchers("/api/notify/**").hasAuthority("SCOPE_notify");
                    // 앞으로 추가되는 공용 API 도 키 인증은 기본으로 받는다(각자 scope 규칙을 위에 추가)
                    auth.requestMatchers("/api/**").authenticated();
                    if (swaggerEnabled) {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    // local 프로파일에서만 실제로 열린다(spring.h2.console.enabled). 다른 프로파일은 404.
                    auth.requestMatchers("/h2-console/**").permitAll();
                    auth.anyRequest().denyAll();
                });
        return http.build();
    }

    /** 기본 사용자(콘솔에 임시 비밀번호 출력)를 만들지 않게 빈 UserDetailsService 를 둔다. */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
