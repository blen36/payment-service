package com.innowise.paymentservice.repository;

import com.innowise.paymentservice.entity.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentQueries {
    private final MongoTemplate mongo;
    public PaymentQueries(MongoTemplate mongo) { this.mongo = mongo; }

    public Page<Payment> find(Long userId, Long orderId, PaymentStatus status, Pageable page) {
        Criteria criteria = criteria(userId, orderId, status);
        long count = mongo.count(Query.query(criteria), Payment.class);
        var rows = mongo.find(Query.query(criteria).with(page), Payment.class);
        return new PageImpl<>(rows, page, count);
    }

    public BigDecimal total(Long userId, Instant from, Instant to, PaymentStatus status) {
        var filter = criteria(userId, null, status).and("timestamp").gte(from).lt(to);
        var pipeline = Aggregation.newAggregation(Aggregation.match(filter),
                Aggregation.group().sum("payment_amount").as("total"));
        Document row = mongo.aggregate(pipeline, "payments", Document.class).getUniqueMappedResult();
        if (row == null) return BigDecimal.ZERO.setScale(2);
        Object value = row.get("total");
        return (value instanceof Decimal128 decimal ? decimal.bigDecimalValue() : new BigDecimal(value.toString())).setScale(2);
    }

    private Criteria criteria(Long userId, Long orderId, PaymentStatus status) {
        Criteria criteria = new Criteria();
        if (userId != null) criteria = criteria.and("user_id").is(userId);
        if (orderId != null) criteria = criteria.and("order_id").is(orderId);
        if (status != null) criteria = criteria.and("status").is(status.name());
        return criteria;
    }
}
