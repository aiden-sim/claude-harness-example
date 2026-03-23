package com.ksa.campaign.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksa.campaign.domain.CampaignStatus;
import com.ksa.campaign.domain.CampaignType;
import com.ksa.campaign.dto.*;
import com.ksa.campaign.exception.CampaignNotFoundException;
import com.ksa.campaign.exception.InvalidParameterException;
import com.ksa.campaign.repository.CampaignRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CampaignService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CampaignRepository campaignRepository;

    public CampaignService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    public PageResponse<CampaignSummaryResponse> getCampaigns(Long accountId, String status, String type,
                                                               Integer limit, String cursor) {
        int pageSize = resolveLimit(limit);
        List<CampaignStatus> statuses = parseStatuses(status);
        CampaignType campaignType = parseType(type);
        Long cursorId = decodeCursor(cursor);

        var campaigns = campaignRepository.findCampaigns(
                accountId, statuses, campaignType, cursorId, PageRequest.of(0, pageSize + 1)
        );

        boolean hasNext = campaigns.size() > pageSize;
        if (hasNext) {
            campaigns = campaigns.subList(0, pageSize);
        }

        long totalCount = campaignRepository.countCampaigns(accountId, statuses, campaignType);

        var data = campaigns.stream()
                .map(CampaignSummaryResponse::from)
                .toList();

        String afterCursor = hasNext && !campaigns.isEmpty()
                ? encodeCursor(campaigns.getLast().getId())
                : null;

        var paging = new PagingInfo(new PagingCursors(afterCursor), hasNext, totalCount);
        return new PageResponse<>(data, paging);
    }

    public SingleResponse<CampaignResponse> getCampaign(Long accountId, Long campaignId) {
        var campaign = campaignRepository.findByIdAndAccountIdAndDeletedAtIsNull(campaignId, accountId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));

        // TODO: adgroupCount, keywordCount는 향후 별도 쿼리로 집계
        var response = CampaignResponse.from(campaign, 0, 0);
        return new SingleResponse<>(response);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidParameterException("limit", "OUT_OF_RANGE",
                    "limit must be between 1 and %d.".formatted(MAX_LIMIT));
        }
        return limit;
    }

    private List<CampaignStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .map(s -> {
                    try {
                        return CampaignStatus.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new InvalidParameterException("status", "INVALID_VALUE",
                                "Invalid status value: '%s'. Allowed: %s".formatted(s,
                                        Arrays.toString(CampaignStatus.values())));
                    }
                })
                .toList();
    }

    private CampaignType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return CampaignType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException("type", "INVALID_VALUE",
                    "Invalid type value: '%s'. Allowed: %s".formatted(type,
                            Arrays.toString(CampaignType.values())));
        }
    }

    private Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            JsonNode node = MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8));
            return node.get("id").asLong();
        } catch (Exception e) {
            throw new InvalidParameterException("cursor", "INVALID_CURSOR",
                    "Invalid cursor format.");
        }
    }

    private String encodeCursor(Long id) {
        try {
            String json = MAPPER.writeValueAsString(new CursorPayload(id));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    private record CursorPayload(Long id) {
    }
}
