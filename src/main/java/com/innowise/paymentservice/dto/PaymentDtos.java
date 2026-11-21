package com.innowise.paymentservice.dto;

import com.innowise.paymentservice.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PaymentDtos {
    private PaymentDtos() {}

    // Owner and amount come from Order Service, never from the caller.
    public record CreatePayment(@NotNull @Positive Long orderId) {}
    public record PaymentView(String id, Long orderId, Long userId, PaymentStatus status,
                              Instant timestamp, BigDecimal paymentAmount) {}
    public record PaymentPage(List<PaymentView> content, int page, int size, long totalElements, int totalPages) {}
    public record PaymentTotal(Long userId, Instant from, Instant to, PaymentStatus status, BigDecimal totalAmount) {}
}
