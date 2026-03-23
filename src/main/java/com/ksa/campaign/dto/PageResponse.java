package com.ksa.campaign.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> data,
        PagingInfo paging
) {
}
