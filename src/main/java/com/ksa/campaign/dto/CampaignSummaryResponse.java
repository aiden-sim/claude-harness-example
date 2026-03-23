package com.ksa.campaign.dto;

import com.ksa.campaign.domain.Campaign;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CampaignSummaryResponse(
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
        Map<String, Object> deviceTargeting,
        List<String> regionTargeting,
        Map<String, Object> schedule,
        BigDecimal dailyBudgetOverdeliveryRate,
        BigDecimal monthlyBudgetCap,
        BigDecimal todaySpend,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CampaignSummaryResponse from(Campaign campaign) {
        return new CampaignSummaryResponse(
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
                campaign.getDeviceTargeting(),
                campaign.getRegionTargeting(),
                campaign.getSchedule(),
                campaign.getDailyBudgetOverdeliveryRate(),
                campaign.getMonthlyBudgetCap(),
                BigDecimal.ZERO,
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }
}
