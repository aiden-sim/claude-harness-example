package com.ksa.campaign.domain;

import com.ksa.campaign.domain.converter.JsonbListConverter;
import com.ksa.campaign.domain.converter.JsonbMapConverter;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "campaign")
@EntityListeners(AuditingEntityListener.class)
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    @Column(name = "daily_budget", nullable = false, precision = 15, scale = 2)
    private BigDecimal dailyBudget;

    @Column(name = "monthly_budget", precision = 15, scale = 2)
    private BigDecimal monthlyBudget;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "bid_strategy_type", nullable = false, length = 20)
    private BidStrategyType bidStrategyType;

    @Column(name = "target_cpa", precision = 15, scale = 2)
    private BigDecimal targetCpa;

    @Column(name = "target_roas", precision = 5, scale = 2)
    private BigDecimal targetRoas;

    @Convert(converter = JsonbMapConverter.class)
    @Column(name = "device_targeting", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> deviceTargeting;

    @Convert(converter = JsonbListConverter.class)
    @Column(name = "region_targeting", columnDefinition = "jsonb")
    private List<String> regionTargeting;

    @Convert(converter = JsonbMapConverter.class)
    @Column(name = "schedule", columnDefinition = "jsonb")
    private Map<String, Object> schedule;

    @Column(name = "daily_budget_overdelivery_rate", nullable = false, precision = 3, scale = 2)
    private BigDecimal dailyBudgetOverdeliveryRate;

    @Column(name = "monthly_budget_cap", precision = 15, scale = 2)
    private BigDecimal monthlyBudgetCap;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    Campaign() {
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public CampaignType getType() {
        return type;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public BigDecimal getDailyBudget() {
        return dailyBudget;
    }

    public BigDecimal getMonthlyBudget() {
        return monthlyBudget;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BidStrategyType getBidStrategyType() {
        return bidStrategyType;
    }

    public BigDecimal getTargetCpa() {
        return targetCpa;
    }

    public BigDecimal getTargetRoas() {
        return targetRoas;
    }

    public Map<String, Object> getDeviceTargeting() {
        return deviceTargeting;
    }

    public List<String> getRegionTargeting() {
        return regionTargeting;
    }

    public Map<String, Object> getSchedule() {
        return schedule;
    }

    public BigDecimal getDailyBudgetOverdeliveryRate() {
        return dailyBudgetOverdeliveryRate;
    }

    public BigDecimal getMonthlyBudgetCap() {
        return monthlyBudgetCap;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
