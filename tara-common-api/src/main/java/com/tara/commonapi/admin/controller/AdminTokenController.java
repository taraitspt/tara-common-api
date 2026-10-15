package com.tara.commonapi.admin.controller;

import com.tara.commonapi.admin.dto.AdminDtos.IssuedToken;
import com.tara.commonapi.admin.dto.AdminDtos.TokenItem;
import com.tara.commonapi.admin.dto.AdminDtos.TokenSave;
import com.tara.commonapi.admin.service.AdminTokenService;
import com.tara.commonapi.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 사람별 관리 토큰 — 마스터 토큰(APP_ADMIN_TOKEN)으로 들어왔을 때만 쓸 수 있다. */
@Tag(name = "AdminToken", description = "관리 화면 토큰 발급·중지 (마스터 전용)")
@RestController
@RequestMapping("/api/admin/tokens")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
public class AdminTokenController {

    private final AdminTokenService service;

    @Operation(summary = "관리 토큰 목록")
    @GetMapping
    public ApiResponse<List<TokenItem>> list() {
        return ApiResponse.ok(service.list());
    }

    @Operation(summary = "관리 토큰 발급 — 응답의 token 은 이때 한 번만 보인다")
    @PostMapping
    public ApiResponse<IssuedToken> create(@Valid @RequestBody TokenSave request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "관리 토큰 수정 (이름·메모·사용 여부)")
    @PutMapping("/{seq}")
    public ApiResponse<Void> update(@PathVariable Long seq, @Valid @RequestBody TokenSave request) {
        service.update(seq, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "관리 토큰 재발급 — 옛 토큰은 즉시 못 쓴다")
    @PostMapping("/{seq}/regenerate")
    public ApiResponse<IssuedToken> regenerate(@PathVariable Long seq) {
        return ApiResponse.ok(service.regenerate(seq));
    }
}
