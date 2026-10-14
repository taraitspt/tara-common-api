package com.tara.commonapi.global.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthFilterTest {

    @Test
    void 허용_IP_CIDR_검사() {
        assertThat(ApiKeyAuthFilter.ipAllowed("10.0.0.0/8, 192.168.1.5", "10.1.2.3")).isTrue();
        assertThat(ApiKeyAuthFilter.ipAllowed("10.0.0.0/8, 192.168.1.5", "192.168.1.5")).isTrue();
        assertThat(ApiKeyAuthFilter.ipAllowed("10.0.0.0/8", "172.16.0.1")).isFalse();
        assertThat(ApiKeyAuthFilter.ipAllowed("잘못된값", "10.0.0.1")).isFalse();
        assertThat(ApiKeyAuthFilter.ipAllowed("10.0.0.0/8", null)).isFalse();
    }
}
