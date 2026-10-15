package com.tara.commonapi.client.controller;

import com.tara.commonapi.client.ApiScopes;
import com.tara.commonapi.client.dto.ClientDtos.ClientItem;
import com.tara.commonapi.client.dto.ClientDtos.ClientSave;
import com.tara.commonapi.client.dto.ClientDtos.HistoryItem;
import com.tara.commonapi.client.dto.ClientDtos.IssuedKey;
import com.tara.commonapi.client.dto.ClientDtos.Reissue;
import com.tara.commonapi.client.service.ApiClientAdminService;
import com.tara.commonapi.global.dto.ApiResponse;
import com.tara.commonapi.notify.dto.NotifyDtos.LogItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ApiClientAdmin", description = "공용 API 사용처·키·권한(scope) 관리")
@RestController
@RequestMapping("/api/admin/clients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ApiClientAdminController {

    private final ApiClientAdminService service;

    @Operation(summary = "사용처 목록 (권한·최근 30일 발송 건수 포함)")
    @GetMapping
    public ApiResponse<List<ClientItem>> list() {
        return ApiResponse.ok(service.list());
    }

    @Operation(summary = "발급 가능한 권한(scope) 목록 — 관리 화면의 선택지")
    @GetMapping("/scopes")
    public ApiResponse<List<String>> scopes() {
        return ApiResponse.ok(ApiScopes.ALL.stream().sorted().toList());
    }

    @Operation(summary = "사용처 등록 — 응답의 apiKey 는 이때 한 번만 보인다. scopes 를 비우면 [\"notify\"]")
    @PostMapping
    public ApiResponse<IssuedKey> create(@Valid @RequestBody ClientSave request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "사용처 수정 (이름·담당·권한·허용 IP·한도·사용 여부)")
    @PutMapping("/{seq}")
    public ApiResponse<Void> update(@PathVariable Long seq, @Valid @RequestBody ClientSave request) {
        service.update(seq, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "키 재발급 — 옛 키는 즉시 못 쓴다. body 의 받는 사람·사유는 이력에 남는다(선택)")
    @PostMapping("/{seq}/regenerate")
    public ApiResponse<IssuedKey> regenerate(@PathVariable Long seq,
                                             @Valid @RequestBody(required = false) Reissue request) {
        return ApiResponse.ok(service.regenerate(seq, request));
    }

    @Operation(summary = "발급·변경 이력 전체 (최근 순)")
    @GetMapping("/history")
    public ApiResponse<List<HistoryItem>> allHistory(@RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(service.history(null, limit));
    }

    @Operation(summary = "사용처별 발급·변경 이력 (최근 순)")
    @GetMapping("/{seq}/history")
    public ApiResponse<List<HistoryItem>> history(@PathVariable Long seq, @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(service.history(seq, limit));
    }

    @Operation(summary = "발송 기록 (최근 순)")
    @GetMapping("/{seq}/logs")
    public ApiResponse<List<LogItem>> logs(@PathVariable Long seq, @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.logs(seq, limit));
    }
}
