package com.innowise.paymentservice.messaging;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "payment.events.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private final PaymentOutboxStore outbox;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final Clock clock;
    private final String topic;
    public PaymentEventPublisher(PaymentOutboxStore outbox, KafkaTemplate<String, String> kafka,
                                 ObjectMapper json, Clock clock, @Value("${payment.events.topic}") String topic) {
        this.outbox = outbox; this.kafka = kafka; this.json = json; this.clock = clock; this.topic = topic;
    }
    @Scheduled(fixedDelayString = "${payment.events.poll-delay-ms:2000}")
    public void publishPending() {
        for (var payment : outbox.pending()) {
            try {
                var event = new PaymentEvent(1, "CREATE_PAYMENT", payment.getId(), payment.getId(),
                        payment.getOrderId(), payment.getUserId(), payment.getStatus().name(),
                        payment.getTimestamp(), payment.getPaymentAmount());
                event.validate();
                // Only mark sent after broker acknowledgement. Crash between send and mark can cause a duplicate.
                kafka.send(topic, payment.getOrderId().toString(), json.writeValueAsString(event)).get(15, TimeUnit.SECONDS);
                outbox.markPublished(payment.getId(), clock.instant());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.warn("Payment event {} remains pending; it will be retried", payment.getId(), ex);
                // Do not overtake an older event during an outage.
                return;
            }
        }
    }
}
