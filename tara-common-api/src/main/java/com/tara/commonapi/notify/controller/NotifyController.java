package com.tara.commonapi.notify.controller;

import com.tara.commonapi.client.entity.ApiClient;
import com.tara.commonapi.global.dto.ApiResponse;
import com.tara.commonapi.global.util.ClientIp;
import com.tara.commonapi.notify.dto.NotifyDtos.SendRequest;
import com.tara.commonapi.notify.dto.NotifyDtos.SendResult;
import com.tara.commonapi.notify.service.NotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 사내 시스템용 Teams 발송 API. 키 인증은 ApiKeyAuthFilter 가 하고(헤더 X-Api-Key),
 * 이 API 는 키에 notify 권한(scope)이 있어야 쓸 수 있다.
 * 서버 전체 IP 제한(APP_IP_ALLOWLIST)이 먼저 걸리므로, 호출 서버 IP 를 거기에도 넣어야 한다.
 */
@Tag(name = "Notify", description = "Teams 발송 API (API 키, scope: notify)")
@RestController
@RequestMapping("/api/notify")
public class NotifyController {

    private final NotifyService service;
    private final boolean trustProxy;

    public NotifyController(NotifyService service, @Value("${app.security.trust-proxy:false}") boolean trustProxy) {
        this.service = service;
        this.trustProxy = trustProxy;
    }

    @Operation(summary = "Teams 1:1 메시지 보내기 — 받는 사람 최대 20명, 본문은 일반 텍스트")
    @PostMapping("/teams")
    @PreAuthorize("hasAuthority('SCOPE_notify')")
    public ApiResponse<SendResult> sendTeams(@AuthenticationPrincipal ApiClient client,
                                             @Valid @RequestBody SendRequest request,
                                             HttpServletRequest http) {
        return ApiResponse.ok(service.send(client, ClientIp.of(http, trustProxy), request));
    }
}
