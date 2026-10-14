package com.tara.commonapi.client.repository;

import com.tara.commonapi.client.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ApiClientRepository extends JpaRepository<ApiClient, Long> {

    Optional<ApiClient> findByKeyHash(String keyHash);

    /** 마지막 호출 시각만 — 감사 컬럼(updated_*)은 건드리지 않는다(호출자는 로그인 사용자가 아니라서) */
    @Transactional
    @Modifying
    @Query("update ApiClient c set c.lastUsedAt = :at where c.clientSeq = :seq")
    void touch(@Param("seq") Long seq, @Param("at") LocalDateTime at);
}
