package com.ksa.campaign.dto;

import com.ksa.campaign.domain.Campaign;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CampaignResponse(
        Long id,
        Long accountId,
        String name,
        String type,
        String status,
        BigDecimal dailyBudget,
        BigDecimal monthlyBudget,
        LocalDate startDate,
        LocalDate endDate,
        String bidStrategyType,
        BigDecimal targetCpa,
        BigDecimal targetRoas,
        Map<String, Object> deviceTargeting,
        List<String> regionTargeting,
        Map<String, Object> schedule,
        BigDecimal dailyBudgetOverdeliveryRate,
        BigDecimal monthlyBudgetCap,
        BigDecimal todaySpend,
        int adgroupCount,
        int keywordCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {

    public static CampaignResponse from(Campaign campaign, int adgroupCount, int keywordCount) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getAccountId(),
                campaign.getName(),
                campaign.getType().name(),
                campaign.getStatus().name(),
                campaign.getDailyBudget(),
                campaign.getMonthlyBudget(),
                campaign.getStartDate(),
                campaign.getEndDate(),
                campaign.getBidStrategyType().name(),
                campaign.getTargetCpa(),
                campaign.getTargetRoas(),
                campaign.getDeviceTargeting(),
                campaign.getRegionTargeting(),
                campaign.getSchedule(),
                campaign.getDailyBudgetOverdeliveryRate(),
                campaign.getMonthlyBudgetCap(),
                BigDecimal.ZERO,
                adgroupCount,
                keywordCount,
                campaign.getCreatedAt(),
                campaign.getUpdatedAt(),
                campaign.getDeletedAt()
        );
    }
}
