package com.tara.commonapi.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 관리 화면 공통 DTO — 로그인 정보, 관리 토큰, API 현황. */
public final class AdminDtos {

    private AdminDtos() {}

    /** 지금 들어온 사람 — master 면 관리 토큰 탭을 보여 준다 */
    public record Me(String name, boolean master) {}

    /* ───────── 관리 토큰 ───────── */

    public record TokenItem(
            Long tokenSeq, String adminName, String keyPrefix, Boolean active, String memo,
            LocalDateTime lastUsedAt, LocalDateTime createdAt, String createdId,
            LocalDateTime updatedAt, String updatedId
    ) {}

    public record TokenSave(
            @NotBlank(message = "관리자 이름을 넣어 주세요.") @Size(max = 20, message = "이름은 20자까지입니다.") String adminName,
            @Size(max = 500) String memo,
            Boolean active
    ) {}

    /** 새 토큰 — 원문은 이때 한 번만 내려간다 */
    public record IssuedToken(Long tokenSeq, String token, String keyPrefix) {}

    /* ───────── API 현황 ───────── */

    public record Overview(
            int clients, int activeClients, int activeAdminTokens,
            long calls30d, long failed30d, List<ApiStat> apis
    ) {}

    /**
     * API(scope) 하나의 현황. usageTracked=false 면 호출 기록이 없는 API(아직 로그 테이블 없음) —
     * calls/daily/top 은 비어 있다.
     */
    public record ApiStat(
            String scope, String label, String path,
            int keys, int activeKeys, int stoppedKeys,
            boolean usageTracked, long calls30d, long failed30d, LocalDateTime lastUsedAt,
            List<DayCount> daily, List<ClientUsage> top
    ) {}

    public record DayCount(LocalDate date, long total, long failed) {}

    public record ClientUsage(Long clientSeq, String clientName, long total, long failed) {}
}
