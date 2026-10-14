package com.tara.commonapi.notify.repository;

import com.tara.commonapi.notify.entity.NotifyLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotifyLogRepository extends JpaRepository<NotifyLog, Long> {

    List<NotifyLog> findByClientSeqOrderBySentAtDesc(Long clientSeq, Pageable pageable);

    /** 사용처별 [전체, 성공] 건수 — 기준 시각 이후 */
    @Query("select l.clientSeq, count(l), sum(case when l.success = true then 1 else 0 end) "
            + "from NotifyLog l where l.sentAt >= :since group by l.clientSeq")
    List<Object[]> countSince(@Param("since") LocalDateTime since);
}
