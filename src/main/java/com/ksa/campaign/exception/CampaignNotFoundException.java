package com.ksa.campaign.exception;

public class CampaignNotFoundException extends RuntimeException {

    private final Long campaignId;

    public CampaignNotFoundException(Long campaignId) {
        super("Campaign with id %d not found.".formatted(campaignId));
        this.campaignId = campaignId;
    }

    public Long getCampaignId() {
        return campaignId;
    }
}
