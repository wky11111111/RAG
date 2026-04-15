package com.team.rag.controller;

import com.team.rag.bean.ApiError;
import com.team.rag.service.ObservabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObservabilityService observabilityService;

    public GlobalExceptionHandler(ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleIllegalArgument(IllegalArgumentException exception) {
        recordException("BAD_REQUEST", exception);
        return new ApiError(exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiError handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        recordException("PAYLOAD_TOO_LARGE", exception);
        return new ApiError("单次请求体过大。普通上传最大 10MB，大文件请使用分片上传。", Instant.now());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleIllegalState(IllegalStateException exception) {
        log.error("Illegal state request failure", exception);
        recordException("INTERNAL_ERROR", exception);
        return new ApiError(exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(RestClientResponseException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleRestClient(RestClientResponseException exception) {
        String message = exception.getResponseBodyAsString();
        if (message == null || message.isBlank()) {
            message = exception.getMessage();
        }
        log.error("Remote API request failed: {}", message, exception);
        recordException("REMOTE_ERROR", exception);
        return new ApiError(message, Instant.now());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleUnexpected(Exception exception) {
        log.error("Unexpected request failure", exception);
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "Unexpected server error";
        }
        recordException("UNEXPECTED_ERROR", exception);
        return new ApiError(message, Instant.now());
    }

    private void recordException(String status, Exception exception) {
        observabilityService.record("api", "exception", status, 0, Map.of(
                "errorType", exception.getClass().getName(),
                "message", exception.getMessage() == null ? "" : exception.getMessage()
        ), exception);
    }
}
