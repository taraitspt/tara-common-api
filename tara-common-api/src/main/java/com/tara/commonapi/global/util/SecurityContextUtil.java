package com.tara.commonapi.global.util;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityContextUtil {

    private SecurityContextUtil() {}

    /** 현재 요청의 사용자 이름. 인증이 없으면 "system" */
    public static String getCurrentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) return "system";
        String name = a.getName();
        return name == null || name.isBlank() ? "system" : name;
    }
}
