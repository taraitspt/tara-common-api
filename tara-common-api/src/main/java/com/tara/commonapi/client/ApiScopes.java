package com.tara.commonapi.client;

import com.tara.commonapi.global.exception.BusinessException;
import com.tara.commonapi.global.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 공용 API 권한(scope) — 키마다 "쓸 수 있는 API"를 정한다.
 * 새 공용 API 를 추가할 때: ① 여기 상수와 ALL·INFO 에 추가 ② 컨트롤러에
 * @PreAuthorize("hasAuthority('SCOPE_xxx')") ③ SecurityConfig 의 경로 규칙에 추가.
 */
public final class ApiScopes {

    /** Teams 1:1 메시지 발송 (/api/notify/**) */
    public static final String NOTIFY = "notify";

    /** 발급 가능한 전체 scope — 관리 API 입력을 이 목록으로 검사한다 */
    public static final Set<String> ALL = Set.of(NOTIFY);

    /** 관리 화면(현황·가이드)에 보이는 이름과 경로 — scope 를 추가하면 여기도 채운다 */
    public static final Map<String, Info> INFO = Map.of(
            NOTIFY, new Info("Teams 알림 발송", "/api/notify/**"));

    public record Info(String label, String path) {}

    public static Info info(String scope) {
        return INFO.getOrDefault(scope, new Info(scope, "-"));
    }

    /** Spring Security 권한 이름 접두사: scope "notify" → authority "SCOPE_notify" */
    public static final String AUTHORITY_PREFIX = "SCOPE_";

    private ApiScopes() {}

    /** DB 에 저장된 쉼표 문자열 → 집합 */
    public static Set<String> parse(String csv) {
        Set<String> out = new LinkedHashSet<>();
        if (!StringUtils.hasText(csv)) return out;
        for (String s : csv.split(",")) {
            if (!s.isBlank()) out.add(s.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** 관리 API 입력 정리 — 비면 notify 만, 모르는 값은 저장 전에 막는다. 저장용 쉼표 문자열을 돌려준다. */
    public static String normalize(List<String> scopes) {
        Set<String> set = new LinkedHashSet<>();
        if (scopes != null) {
            for (String s : scopes) {
                if (s == null || s.isBlank()) continue;
                set.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (set.isEmpty()) set.add(NOTIFY);
        for (String s : set) {
            if (!ALL.contains(s)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "없는 권한(scope)입니다: " + s + " (가능한 값: " + String.join(", ", ALL) + ")");
            }
        }
        return String.join(",", set);
    }
}
