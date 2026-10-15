package com.tara.commonapi.admin.service;

import com.tara.commonapi.admin.dto.AdminDtos.IssuedToken;
import com.tara.commonapi.admin.dto.AdminDtos.TokenItem;
import com.tara.commonapi.admin.dto.AdminDtos.TokenSave;
import com.tara.commonapi.admin.entity.AdminToken;
import com.tara.commonapi.admin.repository.AdminTokenRepository;
import com.tara.commonapi.client.ApiKeys;
import com.tara.commonapi.global.exception.ErrorCode;
import com.tara.commonapi.global.exception.ResourceNotFoundException;
import com.tara.commonapi.global.util.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 사람별 관리 토큰 발급·중지·재발급 (마스터 전용). */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTokenService {

    private final AdminTokenRepository tokens;

    @Transactional(readOnly = true)
    public List<TokenItem> list() {
        return tokens.findAll(Sort.by(Sort.Direction.DESC, "tokenSeq")).stream()
                .map(t -> new TokenItem(t.getTokenSeq(), t.getAdminName(), t.getKeyPrefix(), t.getActive(), t.getMemo(),
                        t.getLastUsedAt(), t.getCreatedAt(), t.getCreatedId(), t.getUpdatedAt(), t.getUpdatedId()))
                .toList();
    }

    @Transactional
    public IssuedToken create(TokenSave req) {
        String raw = ApiKeys.newAdminToken();
        AdminToken t = AdminToken.builder()
                .adminName(req.adminName().trim())
                .memo(blankToNull(req.memo()))
                .active(req.active() == null || req.active())
                .keyPrefix(raw.substring(0, ApiKeys.PREFIX_LEN))
                .keyHash(ApiKeys.sha256(raw))
                .build();
        tokens.save(t);
        log.info("[admin-token] 발급 #{} {} by {}", t.getTokenSeq(), t.getAdminName(), SecurityContextUtil.getCurrentUserId());
        return new IssuedToken(t.getTokenSeq(), raw, t.getKeyPrefix());
    }

    @Transactional
    public void update(Long seq, TokenSave req) {
        AdminToken t = get(seq);
        t.setAdminName(req.adminName().trim());
        t.setMemo(blankToNull(req.memo()));
        if (req.active() != null) t.setActive(req.active());
        log.info("[admin-token] 수정 #{} {} active={} by {}", seq, t.getAdminName(), t.getActive(),
                SecurityContextUtil.getCurrentUserId());
    }

    /** 재발급 — 옛 토큰은 즉시 못 쓴다 */
    @Transactional
    public IssuedToken regenerate(Long seq) {
        AdminToken t = get(seq);
        String raw = ApiKeys.newAdminToken();
        t.setKeyPrefix(raw.substring(0, ApiKeys.PREFIX_LEN));
        t.setKeyHash(ApiKeys.sha256(raw));
        log.info("[admin-token] 재발급 #{} {} by {}", seq, t.getAdminName(), SecurityContextUtil.getCurrentUserId());
        return new IssuedToken(seq, raw, t.getKeyPrefix());
    }

    private AdminToken get(Long seq) {
        return tokens.findById(seq).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
