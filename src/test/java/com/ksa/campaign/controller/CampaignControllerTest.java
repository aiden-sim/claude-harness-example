package com.ksa.campaign.controller;

import com.ksa.campaign.dto.*;
import com.ksa.campaign.exception.CampaignNotFoundException;
import com.ksa.campaign.exception.GlobalExceptionHandler;
import com.ksa.campaign.exception.InvalidParameterException;
import com.ksa.campaign.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CampaignController.class)
@Import(GlobalExceptionHandler.class)
class CampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CampaignService campaignService;

    @Test
    void getCampaigns_returnsPageResponse() throws Exception {
        var summary = new CampaignSummaryResponse(
                1L, 100L, "테스트 캠페인", "SEARCH", "ACTIVE",
                new BigDecimal("50000"), new BigDecimal("1500000"),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31),
                "MANUAL_CPC",
                Map.of("pc", true, "mobile", true),
                List.of("서울특별시"),
                Map.of("monday", List.of(9, 10, 11)),
                new BigDecimal("1.20"), new BigDecimal("1520000"),
                BigDecimal.ZERO,
                LocalDateTime.of(2026, 2, 28, 10, 0),
                LocalDateTime.of(2026, 3, 20, 14, 30)
        );
        var paging = new PagingInfo(new PagingCursors(null), false, 1);
        var response = new PageResponse<>(List.of(summary), paging);

        given(campaignService.getCampaigns(eq(100L), isNull(), isNull(), isNull(), isNull()))
                .willReturn(response);

        mockMvc.perform(get("/v1/campaigns")
                        .header("X-KSA-Account-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("테스트 캠페인"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].daily_budget").value(50000))
                .andExpect(jsonPath("$.paging.has_next").value(false))
                .andExpect(jsonPath("$.paging.total_count").value(1));
    }

    @Test
    void getCampaigns_withStatusFilter() throws Exception {
        var response = new PageResponse<CampaignSummaryResponse>(List.of(),
                new PagingInfo(new PagingCursors(null), false, 0));

        given(campaignService.getCampaigns(eq(100L), eq("ACTIVE,PAUSED"), isNull(), isNull(), isNull()))
                .willReturn(response);

        mockMvc.perform(get("/v1/campaigns")
                        .header("X-KSA-Account-Id", "100")
                        .param("status", "ACTIVE,PAUSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getCampaign_returnsDetail() throws Exception {
        var detail = new CampaignResponse(
                1L, 100L, "테스트 캠페인", "SEARCH", "ACTIVE",
                new BigDecimal("50000"), new BigDecimal("1500000"),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31),
                "MANUAL_CPC", null, null,
                Map.of("pc", true, "mobile", true),
                List.of("서울특별시"),
                Map.of("monday", List.of(9, 10, 11)),
                new BigDecimal("1.20"), new BigDecimal("1520000"),
                BigDecimal.ZERO, 8, 1250,
                LocalDateTime.of(2026, 2, 28, 10, 0),
                LocalDateTime.of(2026, 3, 20, 14, 30),
                null
        );

        given(campaignService.getCampaign(100L, 1L))
                .willReturn(new SingleResponse<>(detail));

        mockMvc.perform(get("/v1/campaigns/1")
                        .header("X-KSA-Account-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.adgroup_count").value(8))
                .andExpect(jsonPath("$.data.keyword_count").value(1250))
                .andExpect(jsonPath("$.data.target_cpa").isEmpty());
    }

    @Test
    void getCampaign_notFound_returns404() throws Exception {
        given(campaignService.getCampaign(100L, 999L))
                .willThrow(new CampaignNotFoundException(999L));

        mockMvc.perform(get("/v1/campaigns/999")
                        .header("X-KSA-Account-Id", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CAMPAIGN_NOT_FOUND"))
                .andExpect(jsonPath("$.error.status").value(404))
                .andExpect(jsonPath("$.error.details[0].field").value("campaign_id"));
    }

    @Test
    void getCampaigns_invalidStatus_returns400() throws Exception {
        given(campaignService.getCampaigns(eq(100L), eq("INVALID"), isNull(), isNull(), isNull()))
                .willThrow(new InvalidParameterException("status", "INVALID_VALUE", "Invalid status value: 'INVALID'."));

        mockMvc.perform(get("/v1/campaigns")
                        .header("X-KSA-Account-Id", "100")
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.error.details[0].field").value("status"));
    }

    @Test
    void getCampaigns_missingAccountHeader_returns401() throws Exception {
        mockMvc.perform(get("/v1/campaigns"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
