package com.innowise.paymentservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.mongodb.core.mapping.*;

@Document(collection = "payments")
public class Payment {
    @MongoId(FieldType.STRING)
    private String id;
    @Field("order_id")
    private Long orderId;
    @Field("user_id")
    private Long userId;
    private PaymentStatus status;
    private Instant timestamp;
    @Field(name = "payment_amount", targetType = FieldType.DECIMAL128)
    private BigDecimal paymentAmount;

    // Embedded outbox: payment and pending event are stored in one atomic MongoDB insert.
    @Field("event_published")
    private boolean eventPublished;
    @Field("event_published_at")
    private Instant eventPublishedAt;

    public boolean isEventPublished() { return eventPublished; }
    public void setEventPublished(boolean value) { this.eventPublished = value; }
    public Instant getEventPublishedAt() { return eventPublishedAt; }
    public void setEventPublishedAt(Instant value) { this.eventPublishedAt = value; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public BigDecimal getPaymentAmount() { return paymentAmount; }
    public void setPaymentAmount(BigDecimal paymentAmount) { this.paymentAmount = paymentAmount; }
}
