package com.ksa.campaign.dto;

public record PagingInfo(
        PagingCursors cursors,
        boolean hasNext,
        long totalCount
) {
}
