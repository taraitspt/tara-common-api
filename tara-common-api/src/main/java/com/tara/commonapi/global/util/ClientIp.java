package com.tara.commonapi.global.util;

import jakarta.servlet.http.HttpServletRequest;

/** 호출한 쪽 IP — 프록시를 믿을 때(trust-proxy=true)만 X-Forwarded-For 첫 값을 쓴다. */
public final class ClientIp {

    private ClientIp() {}

    public static String of(HttpServletRequest request, boolean trustProxy) {
        if (trustProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
