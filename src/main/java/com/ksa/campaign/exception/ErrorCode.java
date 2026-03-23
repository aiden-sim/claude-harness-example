package com.ksa.campaign.exception;

public enum ErrorCode {

    CAMPAIGN_NOT_FOUND(404, "CAMPAIGN_NOT_FOUND"),
    INVALID_PARAMETER(400, "INVALID_PARAMETER"),
    UNAUTHORIZED(401, "UNAUTHORIZED"),
    FORBIDDEN(403, "FORBIDDEN"),
    RATE_LIMIT_EXCEEDED(429, "RATE_LIMIT_EXCEEDED"),
    INTERNAL_ERROR(500, "INTERNAL_ERROR");

    private final int status;
    private final String code;

    ErrorCode(int status, String code) {
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
