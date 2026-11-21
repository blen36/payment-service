package com.innowise.paymentservice.repository;

import com.innowise.paymentservice.entity.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentRepository extends MongoRepository<Payment, String> {
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
