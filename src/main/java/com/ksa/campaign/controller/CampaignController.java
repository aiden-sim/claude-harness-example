package com.ksa.campaign.controller;

import com.ksa.campaign.dto.CampaignResponse;
import com.ksa.campaign.dto.CampaignSummaryResponse;
import com.ksa.campaign.dto.PageResponse;
import com.ksa.campaign.dto.SingleResponse;
import com.ksa.campaign.service.CampaignService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<CampaignSummaryResponse>> getCampaigns(
            @RequestHeader("X-KSA-Account-Id") Long accountId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        var response = campaignService.getCampaigns(accountId, status, type, limit, cursor);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<SingleResponse<CampaignResponse>> getCampaign(
            @RequestHeader("X-KSA-Account-Id") Long accountId,
            @PathVariable Long campaignId
    ) {
        var response = campaignService.getCampaign(accountId, campaignId);
        return ResponseEntity.ok(response);
    }
}
