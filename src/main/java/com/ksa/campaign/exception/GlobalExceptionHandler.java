package com.ksa.campaign.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CampaignNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCampaignNotFound(CampaignNotFoundException e) {
        var detail = new ErrorResponse.Detail(
                "campaign_id",
                "NOT_FOUND",
                "The specified campaign does not exist or has been deleted."
        );
        return buildResponse(ErrorCode.CAMPAIGN_NOT_FOUND, e.getMessage(), List.of(detail));
    }

    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(InvalidParameterException e) {
        var detail = new ErrorResponse.Detail(e.getField(), e.getReason(), e.getMessage());
        return buildResponse(ErrorCode.INVALID_PARAMETER, e.getMessage(), List.of(detail));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        var detail = new ErrorResponse.Detail(
                e.getHeaderName(),
                "MISSING_HEADER",
                e.getMessage()
        );
        ErrorCode code = e.getHeaderName().equals("X-KSA-Account-Id")
                ? ErrorCode.UNAUTHORIZED
                : ErrorCode.INVALID_PARAMETER;
        return buildResponse(code, e.getMessage(), List.of(detail));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        var detail = new ErrorResponse.Detail(
                e.getName(),
                "TYPE_MISMATCH",
                "Expected type: " + (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown")
        );
        return buildResponse(ErrorCode.INVALID_PARAMETER, e.getMessage(), List.of(detail));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        return buildResponse(ErrorCode.INTERNAL_ERROR, "Internal server error.", List.of());
    }

    private ResponseEntity<ErrorResponse> buildResponse(ErrorCode errorCode, String message, List<ErrorResponse.Detail> details) {
        var body = new ErrorResponse.ErrorBody(
                errorCode.getCode(),
                message,
                errorCode.getStatus(),
                details,
                UUID.randomUUID().toString()
        );
        return ResponseEntity.status(errorCode.getStatus()).body(new ErrorResponse(body));
    }
}
