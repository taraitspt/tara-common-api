package com.tara.commonapi.client.service;

import com.tara.commonapi.client.ApiKeys;
import com.tara.commonapi.client.ApiScopes;
import com.tara.commonapi.client.dto.ClientDtos.ClientItem;
import com.tara.commonapi.client.dto.ClientDtos.ClientSave;
import com.tara.commonapi.client.dto.ClientDtos.HistoryItem;
import com.tara.commonapi.client.dto.ClientDtos.IssuedKey;
import com.tara.commonapi.client.dto.ClientDtos.Reissue;
import com.tara.commonapi.client.entity.ApiClient;
import com.tara.commonapi.client.entity.ApiKeyHistory;
import com.tara.commonapi.client.repository.ApiClientRepository;
import com.tara.commonapi.client.repository.ApiKeyHistoryRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/** 공용 API 사용처 관리(관리 토큰) — 등록·수정·키 재발급·발송 기록·발급 이력. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiClientAdminService {

    private final ApiClientRepository clients;
    private final NotifyLogRepository logs;
    private final ApiKeyHistoryRepository history;

    @Transactional(readOnly = true)
    public List<ClientItem> list() {
        Map<Long, long[]> stat = new HashMap<>();
        for (Object[] r : logs.countSince(LocalDateTime.now().minusDays(30))) {
            long total = ((Number) r[1]).longValue();
            long ok = r[2] == null ? 0 : ((Number) r[2]).longValue();
            stat.put((Long) r[0], new long[]{total, total - ok});
        }
        // 사용처별 가장 최근 발급·재발급 (최신순으로 오므로 처음 본 것만 남긴다)
        Map<Long, ApiKeyHistory> lastIssue = new HashMap<>();
        for (ApiKeyHistory h : history.findByActionInOrderByHistorySeqDesc(
                List.of(ApiKeyHistory.ISSUE, ApiKeyHistory.REISSUE))) {
            lastIssue.putIfAbsent(h.getClientSeq(), h);
        }
        return clients.findAll().stream()
                .sorted(Comparator.comparing(ApiClient::getClientSeq))
                .map(c -> {
                    long[] s = stat.getOrDefault(c.getClientSeq(), new long[]{0, 0});
                    ApiKeyHistory h = lastIssue.get(c.getClientSeq());
                    return new ClientItem(c.getClientSeq(), c.getClientName(), c.getOwnerDept(), c.getOwnerName(),
                            c.getKeyPrefix(), List.copyOf(c.scopeSet()), c.getAllowedIps(), c.getRatePerMin(),
                            c.getActive(), c.getMemo(),
                            c.getLastUsedAt(), c.getCreatedAt(), c.getCreatedId(), s[0], s[1],
                            h == null ? null : h.getIssuedTo(), h == null ? null : h.getCreatedAt());
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
        String issuedTo = Optional.ofNullable(blankToNull(req.issuedTo())).orElse(c.getOwnerName());
        record(c, ApiKeyHistory.ISSUE, issuedTo, "권한 " + c.getScopes());
        log.info("[client-admin] 사용처 등록 {} {} scopes={} to={} by {}", c.getClientSeq(), c.getClientName(),
                c.getScopes(), issuedTo, SecurityContextUtil.getCurrentUserId());
        return new IssuedKey(c.getClientSeq(), key, c.getKeyPrefix());
    }

    @Transactional
    public void update(Long seq, ClientSave req) {
        ApiClient c = get(seq);
        boolean wasActive = Boolean.TRUE.equals(c.getActive());
        List<String> changes = new ArrayList<>();

        String name = req.clientName().trim();
        diff(changes, "이름", c.getClientName(), name);
        c.setClientName(name);
        String dept = blankToNull(req.ownerDept());
        diff(changes, "담당 부서", c.getOwnerDept(), dept);
        c.setOwnerDept(dept);
        String owner = blankToNull(req.ownerName());
        diff(changes, "담당자", c.getOwnerName(), owner);
        c.setOwnerName(owner);
        if (req.scopes() != null) {
            String scopes = ApiScopes.normalize(req.scopes());
            diff(changes, "권한", c.getScopes(), scopes);
            c.setScopes(scopes);
        }
        String ips = normalizeIps(req.allowedIps());
        diff(changes, "허용 IP", c.getAllowedIps(), ips);
        c.setAllowedIps(ips);
        if (req.ratePerMin() != null) {
            diff(changes, "분당 한도", c.getRatePerMin(), req.ratePerMin());
            c.setRatePerMin(req.ratePerMin());
        }
        String memo = blankToNull(req.memo());
        diff(changes, "메모", c.getMemo(), memo);
        c.setMemo(memo);
        if (req.active() != null) c.setActive(req.active());

        if (!changes.isEmpty()) record(c, ApiKeyHistory.UPDATE, null, String.join(" / ", changes));
        if (wasActive != Boolean.TRUE.equals(c.getActive())) {
            record(c, c.getActive() ? ApiKeyHistory.RESUME : ApiKeyHistory.STOP, null, null);
        }
        log.info("[client-admin] 사용처 수정 {} active={} scopes={} by {}", seq, c.getActive(), c.getScopes(),
                SecurityContextUtil.getCurrentUserId());
    }

    /** 키 재발급 — 옛 키는 즉시 못 쓴다 */
    @Transactional
    public IssuedKey regenerate(Long seq, Reissue req) {
        ApiClient c = get(seq);
        String oldPrefix = c.getKeyPrefix();
        String key = ApiKeys.newKey();
        c.setKeyPrefix(key.substring(0, ApiKeys.PREFIX_LEN));
        c.setKeyHash(ApiKeys.sha256(key));
        String issuedTo = req == null ? null : blankToNull(req.issuedTo());
        String reason = req == null ? null : blankToNull(req.reason());
        String detail = "이전 키 " + oldPrefix + "… 무효" + (reason == null ? "" : " — " + reason);
        record(c, ApiKeyHistory.REISSUE, issuedTo == null ? c.getOwnerName() : issuedTo, detail);
        log.info("[client-admin] 키 재발급 {} {} to={} by {}", seq, c.getClientName(), issuedTo,
                SecurityContextUtil.getCurrentUserId());
        return new IssuedKey(seq, key, c.getKeyPrefix());
    }

    @Transactional(readOnly = true)
    public List<LogItem> logs(Long seq, int limit) {
        get(seq);
        return logs.findByClientSeqOrderBySentAtDesc(seq, PageRequest.of(0, clamp(limit))).stream()
                .map(l -> new LogItem(l.getLogSeq(), l.getRecipient(), l.getTitle(), l.getMessageLen(), l.getSuccess(),
                        l.getErrorMessage(), l.getRequestIp(), l.getSentAt()))
                .toList();
    }

    /** 발급·변경 이력 — seq 가 null 이면 전체 */
    @Transactional(readOnly = true)
    public List<HistoryItem> history(Long seq, int limit) {
        List<ApiKeyHistory> rows;
        if (seq == null) {
            rows = history.findAllByOrderByHistorySeqDesc(PageRequest.of(0, clamp(limit)));
        } else {
            get(seq);
            rows = history.findByClientSeqOrderByHistorySeqDesc(seq, PageRequest.of(0, clamp(limit)));
        }
        Map<Long, String> names = clients.findAll().stream()
                .collect(Collectors.toMap(ApiClient::getClientSeq, ApiClient::getClientName));
        return rows.stream()
                .map(h -> new HistoryItem(h.getHistorySeq(), h.getClientSeq(), names.get(h.getClientSeq()),
                        h.getAction(), h.getKeyPrefix(), h.getIssuedTo(), h.getDetail(), h.getActor(), h.getCreatedAt()))
                .toList();
    }

    private void record(ApiClient c, String action, String issuedTo, String detail) {
        history.save(ApiKeyHistory.builder()
                .clientSeq(c.getClientSeq())
                .action(action)
                .keyPrefix(c.getKeyPrefix())
                .issuedTo(issuedTo)
                .detail(detail == null ? null : truncate(detail, 500))
                .actor(SecurityContextUtil.getCurrentUserId())
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** 값이 바뀌었으면 "항목: 이전 → 이후" 를 쌓는다 */
    static void diff(List<String> out, String label, Object before, Object after) {
        if (Objects.equals(before, after)) return;
        Function<Object, String> show = v -> v == null ? "(없음)" : v.toString();
        out.add(label + ": " + show.apply(before) + " → " + show.apply(after));
    }

    private ApiClient get(Long seq) {
        return clients.findById(seq).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), 500);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
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
