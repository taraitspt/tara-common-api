package com.tara.commonapi.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    IP_NOT_ALLOWED(HttpStatus.FORBIDDEN, "허용되지 않은 IP 입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 공용 API 키 (ApiKeyAuthFilter)
    API_KEY_INVALID(HttpStatus.UNAUTHORIZED, "API 키가 없거나 올바르지 않습니다."),
    API_KEY_FORBIDDEN(HttpStatus.FORBIDDEN, "이 키로는 호출할 수 없습니다."),

    // Teams 발송 API
    NOTIFY_FORBIDDEN(HttpStatus.FORBIDDEN, "이 키로는 보낼 수 없습니다."),
    NOTIFY_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "발송 한도를 넘었습니다. 잠시 후 다시 시도하세요."),
    NOTIFY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Teams 발송 설정(MS Graph)이 되어 있지 않습니다. 관리자에게 문의하세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
