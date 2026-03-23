package com.ksa.campaign.exception;

import java.util.List;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            int status,
            List<Detail> details,
            String requestId
    ) {
    }

    public record Detail(
            String field,
            String reason,
            String message
    ) {
    }
}
