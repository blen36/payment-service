package com.innowise.paymentservice.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentEvent(int schemaVersion, String eventType, String eventId, String paymentId,
                           Long orderId, Long userId, String status, Instant timestamp, BigDecimal paymentAmount) {
    public void validate() {
        if (schemaVersion != 1 || !"CREATE_PAYMENT".equals(eventType) || eventId == null
                || !eventId.equals(paymentId) || orderId == null || orderId <= 0 || userId == null || userId <= 0
                || !("SUCCESS".equals(status) || "FAILED".equals(status)) || timestamp == null
                || paymentAmount == null || paymentAmount.signum() < 0
                || paymentAmount.stripTrailingZeros().scale() > 2
                || paymentAmount.precision() - paymentAmount.scale() > 17) {
            throw new IllegalArgumentException("Invalid CREATE_PAYMENT event");
        }
        UUID.fromString(eventId);
    }
}
