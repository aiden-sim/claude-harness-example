package com.ksa.campaign.repository;

import com.ksa.campaign.domain.Campaign;
import com.ksa.campaign.domain.CampaignStatus;
import com.ksa.campaign.domain.CampaignType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    @Query("""
            SELECT c FROM Campaign c
            WHERE c.accountId = :accountId
            AND c.deletedAt IS NULL
            AND (:statuses IS NULL OR c.status IN :statuses)
            AND (:type IS NULL OR c.type = :type)
            AND (:cursorId IS NULL OR c.id < :cursorId)
            ORDER BY c.id DESC
            """)
    List<Campaign> findCampaigns(
            @Param("accountId") Long accountId,
            @Param("statuses") List<CampaignStatus> statuses,
            @Param("type") CampaignType type,
            @Param("cursorId") Long cursorId,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
            SELECT COUNT(c) FROM Campaign c
            WHERE c.accountId = :accountId
            AND c.deletedAt IS NULL
            AND (:statuses IS NULL OR c.status IN :statuses)
            AND (:type IS NULL OR c.type = :type)
            """)
    long countCampaigns(
            @Param("accountId") Long accountId,
            @Param("statuses") List<CampaignStatus> statuses,
            @Param("type") CampaignType type
    );

    Optional<Campaign> findByIdAndAccountIdAndDeletedAtIsNull(Long id, Long accountId);
}
