package com.innowise.paymentservice.controller;

import com.innowise.paymentservice.dto.PaymentDtos.*;
import com.innowise.paymentservice.entity.PaymentStatus;
import com.innowise.paymentservice.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;
    public PaymentController(PaymentService payments) { this.payments = payments; }

    @PostMapping
    public ResponseEntity<PaymentView> create(@Valid @RequestBody CreatePayment request, @AuthenticationPrincipal Jwt jwt) {
        var result = payments.create(request, access(jwt), jwt.getTokenValue());
        return ResponseEntity.created(URI.create("/api/v1/payments/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentView> get(@PathVariable @Size(max = 64) String id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(payments.get(id, access(jwt)));
    }

    @GetMapping
    public ResponseEntity<PaymentPage> list(@RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) @Positive Long orderId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(payments.list(userId, orderId, status, page, size, access(jwt)));
    }

    @GetMapping("/total")
    public ResponseEntity<PaymentTotal> total(@RequestParam Instant from, @RequestParam Instant to,
            @RequestParam(required = false) PaymentStatus status, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(payments.totalForCurrentUser(from, to, status, access(jwt)));
    }

    @GetMapping("/total/all")
    public ResponseEntity<PaymentTotal> allTotal(@RequestParam Instant from, @RequestParam Instant to,
            @RequestParam(required = false) PaymentStatus status, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(payments.totalForAllUsers(from, to, status, access(jwt)));
    }

    private PaymentAccess access(Jwt jwt) {
        return new PaymentAccess(Long.parseLong(jwt.getSubject()), "ADMIN".equals(jwt.getClaimAsString("role")));
    }
}
