package com.weddingraffle.rifa.exception;

import com.weddingraffle.rifa.dto.ApiErrorResponse;
import com.weddingraffle.rifa.dto.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorResponse)
                .toList();

        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        OffsetDateTime.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "VALIDATION_ERROR",
                        "Request validation failed.",
                        request.getRequestURI(),
                        fieldErrors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.withoutFieldErrors(
                        HttpStatus.UNAUTHORIZED.value(),
                        "INVALID_CREDENTIALS",
                        "Invalid username or password.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(ExternalPaymentException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalPayment(
            ExternalPaymentException exception, HttpServletRequest request) {
        LOGGER.error(
                "Payment provider error while processing {} {} status={} failureType={} reason={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getHttpStatus(),
                exception.getFailureType(),
                exception.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiErrorResponse.withoutFieldErrors(
                        HttpStatus.BAD_GATEWAY.value(),
                        "PAYMENT_PROVIDER_ERROR",
                        "Unable to communicate with the payment provider.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.withoutFieldErrors(
                        HttpStatus.NOT_FOUND.value(), "NOT_FOUND", exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidWebhookSignature(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.withoutFieldErrors(
                        HttpStatus.UNAUTHORIZED.value(),
                        "INVALID_WEBHOOK_SIGNATURE",
                        "Invalid webhook signature.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(InvalidRaffleStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRaffleState(
            InvalidRaffleStateException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withoutFieldErrors(
                        HttpStatus.CONFLICT.value(),
                        "INVALID_RAFFLE_STATE",
                        exception.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(InvalidTransactionStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransactionState(
            InvalidTransactionStateException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withoutFieldErrors(
                        HttpStatus.CONFLICT.value(),
                        "INVALID_TRANSACTION_STATE",
                        exception.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withoutFieldErrors(
                        HttpStatus.CONFLICT.value(),
                        "IDEMPOTENCY_CONFLICT",
                        exception.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withoutFieldErrors(
                        HttpStatus.BAD_REQUEST.value(),
                        "BAD_REQUEST",
                        exception.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestHeader(
            MissingRequestHeaderException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withoutFieldErrors(
                        HttpStatus.BAD_REQUEST.value(),
                        "BAD_REQUEST",
                        "Required request header is missing.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.withoutFieldErrors(
                        HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "Resource not found.", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error(
                "Unexpected error while processing {} {}", request.getMethod(), request.getRequestURI(), exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.withoutFieldErrors(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_ERROR",
                        "An unexpected error occurred.",
                        request.getRequestURI()));
    }

    private FieldErrorResponse toFieldErrorResponse(FieldError fieldError) {
        return new FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
