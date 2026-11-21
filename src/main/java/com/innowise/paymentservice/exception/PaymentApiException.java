package com.innowise.paymentservice.exception;

import org.springframework.http.HttpStatus;

public class PaymentApiException extends RuntimeException {
    private final HttpStatus status;
    public PaymentApiException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
    public static PaymentApiException invalid(String message) { return new PaymentApiException(HttpStatus.BAD_REQUEST, message); }
    public static PaymentApiException forbidden() { return new PaymentApiException(HttpStatus.FORBIDDEN, "Access denied"); }
    public static PaymentApiException missing(String resource) { return new PaymentApiException(HttpStatus.NOT_FOUND, resource + " not found"); }
    public static PaymentApiException conflict(String message) { return new PaymentApiException(HttpStatus.CONFLICT, message); }
    public static PaymentApiException upstream(String message) { return new PaymentApiException(HttpStatus.BAD_GATEWAY, message); }
}
