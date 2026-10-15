package com.tara.commonapi.client.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 수정 이력에 남길 변경 내용 */
class ApiClientAdminServiceTest {

    @Test
    void 바뀐_항목만_이전_이후로_남긴다() {
        List<String> out = new ArrayList<>();
        ApiClientAdminService.diff(out, "이름", "GROW", "GROW");
        ApiClientAdminService.diff(out, "분당 한도", 30, 60);
        ApiClientAdminService.diff(out, "허용 IP", null, "10.0.0.0/8");
        assertThat(out).containsExactly("분당 한도: 30 → 60", "허용 IP: (없음) → 10.0.0.0/8");
    }
}
