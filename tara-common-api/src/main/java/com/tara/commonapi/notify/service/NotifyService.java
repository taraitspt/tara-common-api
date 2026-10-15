package com.tara.commonapi.notify.service;

import com.tara.commonapi.client.entity.ApiClient;
import com.tara.commonapi.global.exception.BusinessException;
import com.tara.commonapi.global.exception.ErrorCode;
import com.tara.commonapi.notify.dto.NotifyDtos.Recipient;
import com.tara.commonapi.notify.dto.NotifyDtos.SendRequest;
import com.tara.commonapi.notify.dto.NotifyDtos.SendResult;
import com.tara.commonapi.notify.entity.NotifyLog;
import com.tara.commonapi.notify.repository.NotifyLogRepository;
import com.tara.commonapi.teams.TeamsGraphClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Teams 발송 API — 다른 부서 시스템이 API 키(scope: notify)로 Teams 1:1 메시지를 보낸다.
 *
 * 키·IP 인증은 ApiKeyAuthFilter 가 끝내고 사용처(ApiClient)를 넘겨준다.
 * 여기서는: 받는 사람 도메인 → 분당 한도 → 사람마다 발송·기록.
 * 서비스 계정 비밀번호는 이 서버 환경변수(MS_*)에만 있고 사용처는 키만 가진다.
 * 메시지 모양(이모지 제목·본문·기타사항·링크, 또는 카드)은 TeamsMessages 가 만든다 — 사용처 글자는 일반 텍스트로만 다룬다.
 */
@Service
@Slf4j
public class NotifyService {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final NotifyLogRepository logs;
    private final TeamsGraphClient teams;
    private final Set<String> allowedDomains;

    /** 사용처별 최근 1분 발송 시각(ms) — 서버 한 대라 메모리로 충분하다 */
    private final Map<Long, Deque<Long>> window = new ConcurrentHashMap<>();

    public NotifyService(NotifyLogRepository logs, TeamsGraphClient teams,
                         @Value("${app.notify.allowed-domains:taratps.com}") String allowedDomains) {
        this.logs = logs;
        this.teams = teams;
        this.allowedDomains = new HashSet<>();
        for (String d : allowedDomains.split(",")) {
            if (!d.isBlank()) this.allowedDomains.add(d.trim().toLowerCase(Locale.ROOT));
        }
    }

    public SendResult send(ApiClient client, String requestIp, SendRequest req) {
        if (!teams.isConfigured()) throw new BusinessException(ErrorCode.NOTIFY_UNAVAILABLE);

        List<String> to = req.to().stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).distinct().toList();
        for (String r : to) {
            if (!EMAIL.matcher(r).matches()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "받는 사람 이메일 형식이 아닙니다: " + r);
            }
            String domain = r.substring(r.indexOf('@') + 1);
            if (!allowedDomains.isEmpty() && !allowedDomains.contains(domain)) {
                throw new BusinessException(ErrorCode.NOTIFY_FORBIDDEN, "회사 메일(" + String.join(", ", allowedDomains) + ")로만 보낼 수 있습니다: " + r);
            }
        }
        String link = StringUtils.hasText(req.link()) ? req.link().trim() : null;
        if (link != null && !(link.startsWith("https://") || link.startsWith("http://"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "링크는 http:// 또는 https:// 로 시작해야 합니다.");
        }
        reserve(client, to.size());

        Map<String, Object> message = TeamsMessages.chatMessage(new TeamsMessages.Spec(client.getClientName(),
                req.type(), req.icon(), req.title(), req.message(), req.highlight(), req.fields(), req.note(),
                link, req.linkText(), req.style()));
        List<Recipient> results = new ArrayList<>();
        int ok = 0;
        for (String r : to) {
            String err = null;
            try {
                teams.sendMessageToUser(r, message);
                ok++;
            } catch (Exception e) {
                err = shortError(e);
                log.warn("[notify] 발송 실패 client={} to={} : {}", client.getClientName(), r, err);
            }
            results.add(new Recipient(r, err == null, err));
            logs.save(NotifyLog.builder()
                    .clientSeq(client.getClientSeq())
                    .recipient(r)
                    .title(trim(req.title(), 200))
                    .messageLen(req.message().length())
                    .success(err == null)
                    .errorMessage(trim(err, 500))
                    .requestIp(trim(requestIp, 45))
                    .sentAt(LocalDateTime.now())
                    .build());
        }
        log.info("[notify] client={} ip={} 대상 {}명 성공 {}명", client.getClientName(), requestIp, to.size(), ok);
        return new SendResult(to.size(), ok, to.size() - ok, results);
    }

    /** 최근 60초 발송 수 + 이번 건이 한도를 넘으면 거절. 받는 사람 수만큼 센다. */
    private void reserve(ApiClient c, int count) {
        long now = System.currentTimeMillis();
        Deque<Long> q = window.computeIfAbsent(c.getClientSeq(), k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
            int limit = c.getRatePerMin() == null ? 30 : c.getRatePerMin();
            if (q.size() + count > limit) {
                throw new BusinessException(ErrorCode.NOTIFY_RATE_LIMIT,
                        "분당 " + limit + "건 한도를 넘었습니다(최근 1분 " + q.size() + "건). 잠시 후 다시 시도하세요.");
            }
            for (int i = 0; i < count; i++) q.addLast(now);
        }
    }

    /** Graph 오류 본문이 길어서 앞부분만 — 비밀값은 들어 있지 않다(토큰 응답은 예외 메시지에 안 실린다) */
    private static String shortError(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (m.contains("404") || m.contains("Request_ResourceNotFound")) return "받는 사람을 Microsoft 계정에서 찾지 못했습니다";
        return trim(m.replaceAll("\\s+", " "), 300);
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
