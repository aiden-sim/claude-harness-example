package com.ksa.campaign.exception;

public class InvalidParameterException extends RuntimeException {

    private final String field;
    private final String reason;

    public InvalidParameterException(String field, String reason, String message) {
        super(message);
        this.field = field;
        this.reason = reason;
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }
}
