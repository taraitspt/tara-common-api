package com.tara.commonapi.admin.service;

import com.tara.commonapi.admin.dto.AdminDtos.ApiStat;
import com.tara.commonapi.admin.dto.AdminDtos.ClientUsage;
import com.tara.commonapi.admin.dto.AdminDtos.DayCount;
import com.tara.commonapi.admin.dto.AdminDtos.Overview;
import com.tara.commonapi.admin.repository.AdminTokenRepository;
import com.tara.commonapi.client.ApiScopes;
import com.tara.commonapi.client.entity.ApiClient;
import com.tara.commonapi.client.repository.ApiClientRepository;
import com.tara.commonapi.notify.repository.NotifyLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * API(scope)별 키 발급·사용 현황 — 관리 화면 '현황' 탭.
 * 키 수와 최근 사용은 모든 API 공통(api_client), 호출 건수는 기록 테이블이 있는 API 만(지금은 notify → notify_log).
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    static final int DAYS = 30;

    private final ApiClientRepository clients;
    private final NotifyLogRepository notifyLogs;
    private final AdminTokenRepository adminTokens;

    @Transactional(readOnly = true)
    public Overview overview() {
        List<ApiClient> all = clients.findAll();
        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(DAYS - 1L).atStartOfDay();

        // notify 호출 기록 — 사용처별, 날짜별
        Map<Long, long[]> perClient = new HashMap<>();
        for (Object[] r : notifyLogs.countSince(since)) {
            long total = ((Number) r[1]).longValue();
            long ok = r[2] == null ? 0 : ((Number) r[2]).longValue();
            perClient.put((Long) r[0], new long[]{total, total - ok});
        }
        Map<LocalDate, long[]> perDay = new HashMap<>();
        for (Object[] r : notifyLogs.dailySince(since)) {
            long total = ((Number) r[1]).longValue();
            long ok = r[2] == null ? 0 : ((Number) r[2]).longValue();
            perDay.put(toDate(r[0]), new long[]{total, total - ok});
        }

        List<ApiStat> apis = new ArrayList<>();
        for (String scope : ApiScopes.ALL.stream().sorted().toList()) {
            List<ApiClient> holders = all.stream().filter(c -> c.scopeSet().contains(scope)).toList();
            int active = (int) holders.stream().filter(c -> Boolean.TRUE.equals(c.getActive())).count();
            LocalDateTime lastUsed = holders.stream().map(ApiClient::getLastUsedAt).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            ApiScopes.Info info = ApiScopes.info(scope);

            boolean tracked = ApiScopes.NOTIFY.equals(scope);
            List<DayCount> daily = new ArrayList<>();
            List<ClientUsage> top = new ArrayList<>();
            long calls = 0, failed = 0;
            if (tracked) {
                for (int i = DAYS - 1; i >= 0; i--) {
                    LocalDate d = today.minusDays(i);
                    long[] v = perDay.getOrDefault(d, new long[]{0, 0});
                    daily.add(new DayCount(d, v[0], v[1]));
                    calls += v[0];
                    failed += v[1];
                }
                for (ApiClient c : holders) {
                    long[] v = perClient.get(c.getClientSeq());
                    if (v != null) top.add(new ClientUsage(c.getClientSeq(), c.getClientName(), v[0], v[1]));
                }
                top.sort(Comparator.comparingLong(ClientUsage::total).reversed());
                if (top.size() > 10) top = new ArrayList<>(top.subList(0, 10));
            }
            apis.add(new ApiStat(scope, info.label(), info.path(), holders.size(), active, holders.size() - active,
                    tracked, calls, failed, lastUsed, daily, top));
        }

        int activeClients = (int) all.stream().filter(c -> Boolean.TRUE.equals(c.getActive())).count();
        int activeTokens = (int) adminTokens.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getActive())).count();
        long calls = apis.stream().mapToLong(ApiStat::calls30d).sum();
        long failed = apis.stream().mapToLong(ApiStat::failed30d).sum();
        return new Overview(all.size(), activeClients, activeTokens, calls, failed, apis);
    }

    /** 드라이버마다 날짜 타입이 다르다(java.sql.Date / LocalDate / 문자열) */
    static LocalDate toDate(Object v) {
        if (v instanceof LocalDate d) return d;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof LocalDateTime t) return t.toLocalDate();
        return LocalDate.parse(v.toString().substring(0, 10));
    }
}
