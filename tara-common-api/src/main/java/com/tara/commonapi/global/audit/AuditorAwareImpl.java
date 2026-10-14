package com.tara.commonapi.global.audit;

import com.tara.commonapi.global.util.SecurityContextUtil;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 감사 컬럼(created_id/updated_id)에 들어갈 이름 — 관리 토큰으로 들어온 요청은 "admin", 그 외 "system" */
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(SecurityContextUtil.getCurrentUserId());
    }
}
