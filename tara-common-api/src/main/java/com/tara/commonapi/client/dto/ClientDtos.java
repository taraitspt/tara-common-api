package com.tara.commonapi.client.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 공용 API 사용처 관리 DTO. */
public final class ClientDtos {

    private ClientDtos() {}

    public record ClientItem(
            Long clientSeq, String clientName, String ownerDept, String ownerName,
            String keyPrefix, List<String> scopes, String allowedIps, Integer ratePerMin, Boolean active, String memo,
            LocalDateTime lastUsedAt, LocalDateTime createdAt, String createdId,
            long sent30d, long failed30d
    ) {}

    public record ClientSave(
            @NotBlank(message = "사용처 이름을 넣어 주세요.") @Size(max = 100) String clientName,
            @Size(max = 100) String ownerDept,
            @Size(max = 50) String ownerName,
            /** 쓸 수 있는 API 목록(ApiScopes). 비우면 ["notify"] */
            @Size(max = 10, message = "권한(scope)은 10개까지입니다.") List<String> scopes,
            @Size(max = 500, message = "허용 IP 는 500자까지입니다.") String allowedIps,
            @Min(value = 1, message = "분당 한도는 1 이상입니다.") @Max(value = 300, message = "분당 한도는 300 이하입니다.") Integer ratePerMin,
            Boolean active,
            @Size(max = 500) String memo
    ) {}

    /** 새 키 — 원문은 이때 한 번만 내려간다 */
    public record IssuedKey(Long clientSeq, String apiKey, String keyPrefix) {}
}
