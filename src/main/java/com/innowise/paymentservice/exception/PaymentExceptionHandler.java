package com.innowise.paymentservice.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class PaymentExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentExceptionHandler.class);

    @ExceptionHandler(PaymentApiException.class)
    ResponseEntity<ProblemDetail> application(PaymentApiException ex) { return problem(ex.status(), ex.getMessage()); }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> validation(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameters");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> database(DataAccessException ex) {
        log.error("Payment database operation failed", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Payment database is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Unexpected payment request failure", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message));
    }
}
