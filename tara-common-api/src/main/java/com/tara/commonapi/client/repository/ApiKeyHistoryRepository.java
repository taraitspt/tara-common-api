package com.tara.commonapi.client.repository;

import com.tara.commonapi.client.entity.ApiKeyHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ApiKeyHistoryRepository extends JpaRepository<ApiKeyHistory, Long> {

    List<ApiKeyHistory> findByClientSeqOrderByHistorySeqDesc(Long clientSeq, Pageable pageable);

    List<ApiKeyHistory> findAllByOrderByHistorySeqDesc(Pageable pageable);

    /** 사용처별 최근 발급을 고르는 용도 — 최신순 */
    List<ApiKeyHistory> findByActionInOrderByHistorySeqDesc(Collection<String> actions);
}
