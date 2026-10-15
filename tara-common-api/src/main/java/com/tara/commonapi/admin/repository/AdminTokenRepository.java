package com.tara.commonapi.admin.repository;

import com.tara.commonapi.admin.entity.AdminToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AdminTokenRepository extends JpaRepository<AdminToken, Long> {

    Optional<AdminToken> findByKeyHash(String keyHash);

    /** 마지막 사용 시각만 — 감사 컬럼(updated_*)은 건드리지 않는다 */
    @Transactional
    @Modifying
    @Query("update AdminToken t set t.lastUsedAt = :at where t.tokenSeq = :seq")
    void touch(@Param("seq") Long seq, @Param("at") LocalDateTime at);
}
