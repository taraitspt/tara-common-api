package com.tara.commonapi.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * API 키 생성·해시.
 * 키 모양: tara_ + 무작위 32바이트(base64url). DB 에는 SHA-256 해시만 두고 원문은 발급 응답에서 한 번만 보여 준다.
 */
public final class ApiKeys {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String KEY_HEAD = "tara_";
    /** 관리 화면 토큰 앞머리 — API 키(tara_)와 눈으로 구분되게 */
    public static final String ADMIN_HEAD = "tadm_";
    /** 목록에서 알아보는 용도로 저장하는 키 앞부분 길이 */
    public static final int PREFIX_LEN = 12;

    private ApiKeys() {}

    public static String newKey() {
        return newKey(KEY_HEAD);
    }

    public static String newAdminToken() {
        return newKey(ADMIN_HEAD);
    }

    private static String newKey(String head) {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return head + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
