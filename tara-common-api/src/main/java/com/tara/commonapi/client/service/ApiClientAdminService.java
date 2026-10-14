package com.tara.commonapi.client.service;

import com.tara.commonapi.client.ApiKeys;
import com.tara.commonapi.client.ApiScopes;
import com.tara.commonapi.client.dto.ClientDtos.ClientItem;
import com.tara.commonapi.client.dto.ClientDtos.ClientSave;
import com.tara.commonapi.client.dto.ClientDtos.IssuedKey;
import com.tara.commonapi.client.entity.ApiClient;
import com.tara.commonapi.client.repository.ApiClientRepository;
import com.tara.commonapi.global.exception.BusinessException;
import com.tara.commonapi.global.exception.ErrorCode;
import com.tara.commonapi.global.exception.ResourceNotFoundException;
import com.tara.commonapi.global.util.SecurityContextUtil;
import com.tara.commonapi.notify.dto.NotifyDtos.LogItem;
import com.tara.commonapi.notify.repository.NotifyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/** 공용 API 사용처 관리(관리 토큰) — 등록·수정·키 재발급·발송 기록 조회. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiClientAdminService {

    private final ApiClientRepository clients;
    private final NotifyLogRepository logs;

    @Transactional(readOnly = true)
    public List<ClientItem> list() {
        Map<Long, long[]> stat = new HashMap<>();
        for (Object[] r : logs.countSince(LocalDateTime.now().minusDays(30))) {
            long total = ((Number) r[1]).longValue();
            long ok = r[2] == null ? 0 : ((Number) r[2]).longValue();
            stat.put((Long) r[0], new long[]{total, total - ok});
        }
        return clients.findAll().stream()
                .sorted(Comparator.comparing(ApiClient::getClientSeq))
                .map(c -> {
                    long[] s = stat.getOrDefault(c.getClientSeq(), new long[]{0, 0});
                    return new ClientItem(c.getClientSeq(), c.getClientName(), c.getOwnerDept(), c.getOwnerName(),
                            c.getKeyPrefix(), List.copyOf(c.scopeSet()), c.getAllowedIps(), c.getRatePerMin(),
                            c.getActive(), c.getMemo(),
                            c.getLastUsedAt(), c.getCreatedAt(), c.getCreatedId(), s[0], s[1]);
                })
                .toList();
    }

    @Transactional
    public IssuedKey create(ClientSave req) {
        String key = ApiKeys.newKey();
        ApiClient c = ApiClient.builder()
                .clientName(req.clientName().trim())
                .ownerDept(blankToNull(req.ownerDept()))
                .ownerName(blankToNull(req.ownerName()))
                .scopes(ApiScopes.normalize(req.scopes()))
                .allowedIps(normalizeIps(req.allowedIps()))
                .ratePerMin(req.ratePerMin() == null ? 30 : req.ratePerMin())
                .active(req.active() == null || req.active())
                .memo(blankToNull(req.memo()))
                .keyPrefix(key.substring(0, ApiKeys.PREFIX_LEN))
                .keyHash(ApiKeys.sha256(key))
                .build();
        clients.save(c);
        log.info("[client-admin] 사용처 등록 {} {} scopes={} by {}", c.getClientSeq(), c.getClientName(),
                c.getScopes(), SecurityContextUtil.getCurrentUserId());
        return new IssuedKey(c.getClientSeq(), key, c.getKeyPrefix());
    }

    @Transactional
    public void update(Long seq, ClientSave req) {
        ApiClient c = get(seq);
        c.setClientName(req.clientName().trim());
        c.setOwnerDept(blankToNull(req.ownerDept()));
        c.setOwnerName(blankToNull(req.ownerName()));
        if (req.scopes() != null) c.setScopes(ApiScopes.normalize(req.scopes()));
        c.setAllowedIps(normalizeIps(req.allowedIps()));
        if (req.ratePerMin() != null) c.setRatePerMin(req.ratePerMin());
        if (req.active() != null) c.setActive(req.active());
        c.setMemo(blankToNull(req.memo()));
        log.info("[client-admin] 사용처 수정 {} active={} scopes={} by {}", seq, c.getActive(), c.getScopes(),
                SecurityContextUtil.getCurrentUserId());
    }

    /** 키 재발급 — 옛 키는 즉시 못 쓴다 */
    @Transactional
    public IssuedKey regenerate(Long seq) {
        ApiClient c = get(seq);
        String key = ApiKeys.newKey();
        c.setKeyPrefix(key.substring(0, ApiKeys.PREFIX_LEN));
        c.setKeyHash(ApiKeys.sha256(key));
        log.info("[client-admin] 키 재발급 {} {} by {}", seq, c.getClientName(), SecurityContextUtil.getCurrentUserId());
        return new IssuedKey(seq, key, c.getKeyPrefix());
    }

    @Transactional(readOnly = true)
    public List<LogItem> logs(Long seq, int limit) {
        get(seq);
        return logs.findByClientSeqOrderBySentAtDesc(seq, PageRequest.of(0, Math.min(Math.max(limit, 1), 500))).stream()
                .map(l -> new LogItem(l.getLogSeq(), l.getRecipient(), l.getTitle(), l.getMessageLen(), l.getSuccess(),
                        l.getErrorMessage(), l.getRequestIp(), l.getSentAt()))
                .toList();
    }

    private ApiClient get(Long seq) {
        return clients.findById(seq).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 쉼표 목록 정리 + CIDR 형식 검사(잘못된 값은 저장 전에 막는다) */
    private static String normalizeIps(String csv) {
        if (!StringUtils.hasText(csv)) return null;
        List<String> out = new ArrayList<>();
        for (String s : csv.split("[,\\s]+")) {
            String v = s.trim();
            if (v.isEmpty()) continue;
            try {
                new IpAddressMatcher(v);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "IP/CIDR 형식이 아닙니다: " + v);
            }
            out.add(v);
        }
        return out.isEmpty() ? null : String.join(",", out);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
