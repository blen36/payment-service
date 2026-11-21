package com.innowise.paymentservice.messaging;

import com.innowise.paymentservice.entity.Payment;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOutboxStore {
    private final MongoTemplate mongo;
    public PaymentOutboxStore(MongoTemplate mongo) { this.mongo = mongo; }
    public List<Payment> pending() {
        return mongo.find(Query.query(Criteria.where("event_published").is(false))
                .with(Sort.by("timestamp", "id")).limit(100), Payment.class);
    }
    public void markPublished(String id, Instant now) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("event_published").is(false)),
                new Update().set("event_published", true).set("event_published_at", now), Payment.class);
    }
}
