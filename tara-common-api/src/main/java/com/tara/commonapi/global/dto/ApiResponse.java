package com.tara.commonapi.global.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API 의 공통 응답 모양.
 * 성공: {"success":true,"data":...}
 * 실패: {"success":false,"code":"NOTIFY_KEY_INVALID","message":"..."}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, null, data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
