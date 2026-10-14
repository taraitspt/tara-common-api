package com.tara.commonapi.client;

import com.tara.commonapi.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiScopesTest {

    @Test
    void 비우면_notify_만() {
        assertThat(ApiScopes.normalize(null)).isEqualTo("notify");
        assertThat(ApiScopes.normalize(List.of())).isEqualTo("notify");
    }

    @Test
    void 대소문자_공백_중복을_정리한다() {
        assertThat(ApiScopes.normalize(List.of(" Notify ", "notify"))).isEqualTo("notify");
    }

    @Test
    void 모르는_scope_는_저장_전에_막는다() {
        assertThatThrownBy(() -> ApiScopes.normalize(List.of("notify", "없는거")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("없는거");
    }

    @Test
    void 저장된_쉼표_문자열을_집합으로() {
        assertThat(ApiScopes.parse("notify, NOTIFY ,")).containsExactly("notify");
        assertThat(ApiScopes.parse(null)).isEmpty();
    }
}
