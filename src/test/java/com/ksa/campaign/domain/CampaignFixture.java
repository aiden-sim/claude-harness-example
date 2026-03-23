package com.ksa.campaign.domain;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CampaignFixture {

    private CampaignFixture() {
    }

    public static Campaign create(Long id) {
        var campaign = new Campaign();
        ReflectionTestUtils.setField(campaign, "id", id);
        ReflectionTestUtils.setField(campaign, "accountId", 1L);
        ReflectionTestUtils.setField(campaign, "name", "테스트 캠페인");
        ReflectionTestUtils.setField(campaign, "type", CampaignType.SEARCH);
        ReflectionTestUtils.setField(campaign, "status", CampaignStatus.ACTIVE);
        ReflectionTestUtils.setField(campaign, "dailyBudget", new BigDecimal("50000"));
        ReflectionTestUtils.setField(campaign, "monthlyBudget", new BigDecimal("1500000"));
        ReflectionTestUtils.setField(campaign, "startDate", LocalDate.of(2026, 3, 1));
        ReflectionTestUtils.setField(campaign, "endDate", LocalDate.of(2026, 5, 31));
        ReflectionTestUtils.setField(campaign, "bidStrategyType", BidStrategyType.MANUAL_CPC);
        ReflectionTestUtils.setField(campaign, "deviceTargeting", Map.of("pc", true, "mobile", true));
        ReflectionTestUtils.setField(campaign, "regionTargeting", List.of("서울특별시"));
        ReflectionTestUtils.setField(campaign, "schedule", Map.of("monday", List.of(9, 10, 11)));
        ReflectionTestUtils.setField(campaign, "dailyBudgetOverdeliveryRate", new BigDecimal("1.20"));
        ReflectionTestUtils.setField(campaign, "monthlyBudgetCap", new BigDecimal("1520000"));
        ReflectionTestUtils.setField(campaign, "createdAt", LocalDateTime.of(2026, 2, 28, 10, 0));
        ReflectionTestUtils.setField(campaign, "updatedAt", LocalDateTime.of(2026, 3, 20, 14, 30));
        return campaign;
    }
}
