package com.tara.commonapi.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeysTest {

    @Test
    void sha256_hex_64자() {
        assertThat(ApiKeys.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void 새_키는_tara_로_시작하고_앞부분을_잘라_쓸_수_있다() {
        String key = ApiKeys.newKey();
        assertThat(key).startsWith(ApiKeys.KEY_HEAD);
        assertThat(key.length()).isGreaterThan(ApiKeys.PREFIX_LEN);
        assertThat(ApiKeys.newKey()).isNotEqualTo(key);
    }
}
