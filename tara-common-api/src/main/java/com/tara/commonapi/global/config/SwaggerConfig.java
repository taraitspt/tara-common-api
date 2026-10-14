package com.tara.commonapi.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger — app.swagger.enabled=true 일 때만(운영 prod 는 false). 화면 우측 Authorize 에 키를 넣고 시험한다. */
@Configuration
@ConditionalOnProperty(name = "app.swagger.enabled", havingValue = "true")
public class SwaggerConfig {

    public static final String API_KEY = "ApiKey";
    public static final String ADMIN_TOKEN = "AdminToken";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tara Common API")
                        .version("v1")
                        .description("타라 사내 시스템 공용 API — Teams 1:1 메시지 발송 등"))
                .components(new Components()
                        .addSecuritySchemes(API_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(ApiKeyAuthFilter.HEADER)
                                .description("사용처별 API 키 (관리 API 로 발급, 권한은 키의 scope 를 따른다)"))
                        .addSecuritySchemes(ADMIN_TOKEN, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(AdminTokenFilter.HEADER)
                                .description("관리 토큰 (서버 env APP_ADMIN_TOKEN)")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY).addList(ADMIN_TOKEN));
    }
}
