package com.innowise.paymentservice.service;

import com.innowise.paymentservice.client.*;
import com.innowise.paymentservice.dto.PaymentDtos.*;
import com.innowise.paymentservice.entity.*;
import com.innowise.paymentservice.exception.PaymentApiException;
import com.innowise.paymentservice.mapper.PaymentMapper;
import com.innowise.paymentservice.repository.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentQueries queries;
    private final PaymentMapper mapper;
    private final OrderClient orders;
    private final RandomNumberClient random;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentQueries queries, PaymentMapper mapper,
                          OrderClient orders, RandomNumberClient random, Clock clock) {
        this.payments = payments; this.queries = queries; this.mapper = mapper;
        this.orders = orders; this.random = random; this.clock = clock;
    }

    public PaymentView create(CreatePayment request, PaymentAccess access, String token) {
        var order = orders.get(request.orderId(), token);
        access.requireOwner(order.user().id());
        if (!"NEW".equals(order.status()) && !"PAYMENT_FAILED".equals(order.status())) {
            throw PaymentApiException.conflict("Only a NEW or PAYMENT_FAILED order can be paid");
        }
        if (payments.existsByOrderIdAndStatus(request.orderId(), PaymentStatus.SUCCESS)) {
            throw PaymentApiException.conflict("This order already has a successful payment");
        }
        int number = random.nextNumber();
        var payment = mapper.toEntity(request, order);
        payment.setId(UUID.randomUUID().toString());
        payment.setPaymentAmount(payment.getPaymentAmount().setScale(2));
        payment.setTimestamp(clock.instant().truncatedTo(ChronoUnit.MILLIS));
        payment.setStatus(number % 2 == 0 ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        try {
            // insert + a partial unique index also protects against concurrent successful requests.
            return mapper.toView(payments.insert(payment));
        } catch (DuplicateKeyException ex) {
            throw PaymentApiException.conflict("This order already has a successful payment");
        }
    }

    public PaymentView get(String id, PaymentAccess access) {
        var payment = payments.findById(id).orElseThrow(() -> PaymentApiException.missing("Payment"));
        access.requireOwner(payment.getUserId());
        return mapper.toView(payment);
    }

    public PaymentPage list(Long userId, Long orderId, PaymentStatus status, int page, int size, PaymentAccess access) {
        if (page < 0 || size < 1 || size > 100) throw PaymentApiException.invalid("Invalid pagination");
        var paging = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp", "id"));
        var result = queries.find(access.listOwner(userId), orderId, status, paging).map(mapper::toView);
        return new PaymentPage(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    public PaymentTotal totalForCurrentUser(Instant from, Instant to, PaymentStatus status, PaymentAccess access) {
        return total(access.userId(), from, to, status);
    }

    public PaymentTotal totalForAllUsers(Instant from, Instant to, PaymentStatus status, PaymentAccess access) {
        access.requireAdmin();
        return total(null, from, to, status);
    }

    private PaymentTotal total(Long userId, Instant from, Instant to, PaymentStatus status) {
        if (from == null || to == null || !from.isBefore(to)) throw PaymentApiException.invalid("from must be earlier than to");
        return new PaymentTotal(userId, from, to, status, queries.total(userId, from, to, status));
    }
}
