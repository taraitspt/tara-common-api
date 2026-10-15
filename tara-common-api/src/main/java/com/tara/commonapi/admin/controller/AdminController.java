package com.tara.commonapi.admin.controller;

import com.tara.commonapi.admin.dto.AdminDtos.Me;
import com.tara.commonapi.admin.dto.AdminDtos.Overview;
import com.tara.commonapi.admin.service.AdminStatsService;
import com.tara.commonapi.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "관리 화면 공통 — 로그인 정보·API 현황")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminStatsService stats;

    @Operation(summary = "지금 들어온 관리자 (이름·마스터 여부)")
    @GetMapping("/me")
    public ApiResponse<Me> me(Authentication auth) {
        boolean master = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MASTER".equals(a.getAuthority()));
        return ApiResponse.ok(new Me(auth.getName(), master));
    }

    @Operation(summary = "API 별 키 발급·사용 현황 (최근 30일)")
    @GetMapping("/stats")
    public ApiResponse<Overview> stats() {
        return ApiResponse.ok(stats.overview());
    }
}
