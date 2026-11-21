package com.innowise.paymentservice.messaging;

import com.innowise.paymentservice.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherTest {
    @Mock PaymentOutboxStore outbox;
    @Mock KafkaTemplate<String,String> kafka;
    PaymentEventPublisher publisher;
    final Instant now=Instant.parse("2026-11-21T00:00:00Z");
    @BeforeEach void init() { publisher=new PaymentEventPublisher(outbox,kafka,JsonMapper.builder().build(),Clock.fixed(now,ZoneOffset.UTC),"payment-events"); }
    Payment payment(PaymentStatus status) {
        var p=new Payment();p.setId(UUID.randomUUID().toString());p.setOrderId(12L);p.setUserId(7L);
        p.setTimestamp(now);p.setPaymentAmount(new BigDecimal("19.90"));p.setStatus(status);return p;
    }
    @Test void publishesContractAndMarksOnlyAfterBrokerAck() throws Exception {
        var p=payment(PaymentStatus.SUCCESS);when(outbox.pending()).thenReturn(List.of(p));
        when(kafka.send(eq("payment-events"),eq("12"),anyString())).thenReturn(CompletableFuture.completedFuture(null));
        publisher.publishPending();
        var body=ArgumentCaptor.forClass(String.class);
        var sequence=inOrder(kafka,outbox);
        sequence.verify(kafka).send(eq("payment-events"),eq("12"),body.capture());
        sequence.verify(outbox).markPublished(p.getId(),now);
        var event=JsonMapper.builder().build().readValue(body.getValue(),PaymentEvent.class);
        assertThat(event.eventId()).isEqualTo(p.getId());assertThat(event.eventType()).isEqualTo("CREATE_PAYMENT");
        assertThat(event.paymentAmount()).isEqualByComparingTo("19.90");event.validate();
    }
    @Test void publishesFailedPaymentsToo() {
        var p=payment(PaymentStatus.FAILED);when(outbox.pending()).thenReturn(List.of(p));
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.completedFuture(null));
        publisher.publishPending();verify(kafka).send(eq("payment-events"),eq("12"),contains("FAILED"));
    }
    @Test void retainsPendingEventOnBrokerFailure() {
        when(outbox.pending()).thenReturn(List.of(payment(PaymentStatus.SUCCESS)));
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        publisher.publishPending();verify(outbox,never()).markPublished(anyString(),any());
    }
    @Test void retriesSameEventIdAfterFailure() {
        var p=payment(PaymentStatus.SUCCESS);when(outbox.pending()).thenReturn(List.of(p));
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(
                CompletableFuture.failedFuture(new RuntimeException("broker down")),CompletableFuture.completedFuture(null));
        publisher.publishPending();publisher.publishPending();
        verify(outbox).markPublished(p.getId(),now);
        verify(kafka,times(2)).send(eq("payment-events"),eq("12"),contains(p.getId()));
    }
    @Test void retriesWhenDatabaseMarkFailsAfterSuccessfulSend() {
        var p=payment(PaymentStatus.SUCCESS);when(outbox.pending()).thenReturn(List.of(p));
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.completedFuture(null));
        doThrow(new RuntimeException("database down")).doNothing().when(outbox).markPublished(p.getId(),now);
        publisher.publishPending();publisher.publishPending();
        verify(kafka,times(2)).send(eq("payment-events"),eq("12"),contains(p.getId()));
    }
    @Test void stopsBatchOnFailureToKeepOrder() {
        when(outbox.pending()).thenReturn(List.of(payment(PaymentStatus.FAILED),payment(PaymentStatus.SUCCESS)));
        when(kafka.send(anyString(),anyString(),anyString())).thenThrow(new RuntimeException("offline"));
        publisher.publishPending();verify(kafka,times(1)).send(anyString(),anyString(),anyString());
    }
    @Test void emptyOutboxDoesNothing() { when(outbox.pending()).thenReturn(List.of());publisher.publishPending();verifyNoInteractions(kafka); }
}
