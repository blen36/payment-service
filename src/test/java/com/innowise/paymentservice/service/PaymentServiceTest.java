package com.innowise.paymentservice.service;

import com.innowise.paymentservice.client.*;
import com.innowise.paymentservice.client.OrderClient.*;
import com.innowise.paymentservice.dto.PaymentDtos.*;
import com.innowise.paymentservice.entity.*;
import com.innowise.paymentservice.exception.PaymentApiException;
import com.innowise.paymentservice.mapper.PaymentMapper;
import com.innowise.paymentservice.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock PaymentRepository repository;
    @Mock PaymentQueries queries;
    @Mock OrderClient orders;
    @Mock RandomNumberClient random;
    PaymentService service;
    final PaymentAccess user = new PaymentAccess(7, false);
    final PaymentAccess admin = new PaymentAccess(1, true);
    final Instant now = Instant.parse("2026-11-21T12:00:00Z");

    @BeforeEach void setup() {
        service = new PaymentService(repository, queries, Mappers.getMapper(PaymentMapper.class), orders, random,
                Clock.fixed(now, ZoneOffset.UTC));
    }
    OrderSnapshot order(long owner, String status) {
        return new OrderSnapshot(12L, status, new BigDecimal("19.90"), new OrderUser(owner));
    }
    Payment payment() {
        var p = new Payment(); p.setId("payment-1"); p.setOrderId(12L); p.setUserId(7L);
        p.setPaymentAmount(new BigDecimal("19.90")); p.setStatus(PaymentStatus.SUCCESS); p.setTimestamp(now);
        return p;
    }
    void expectStatus(Runnable work, HttpStatus status) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(PaymentApiException.class,
                ex -> assertThat(ex.status()).isEqualTo(status));
    }

    @ParameterizedTest @CsvSource({"2,SUCCESS", "3,FAILED"})
    void createsPaymentUsingTrustedOrderAndExternalNumber(int number, PaymentStatus expected) {
        when(orders.get(12, "token")).thenReturn(order(7, "NEW"));
        when(random.nextNumber()).thenReturn(number);
        when(repository.insert(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        var actual = service.create(new CreatePayment(12L), user, "token");
        assertThat(actual.status()).isEqualTo(expected);
        assertThat(actual.userId()).isEqualTo(7L);
        assertThat(actual.paymentAmount()).isEqualByComparingTo("19.90");
        assertThat(actual.timestamp()).isEqualTo(now);
        assertThatCode(() -> UUID.fromString(actual.id())).doesNotThrowAnyException();
    }

    @Test void forbidsOtherUsersOrderBeforeCallingProvider() {
        when(orders.get(12, "token")).thenReturn(order(8, "NEW"));
        expectStatus(() -> service.create(new CreatePayment(12L), user, "token"), HttpStatus.FORBIDDEN);
        verifyNoInteractions(random, repository);
    }
    @Test void adminPaymentStillBelongsToOrderOwner() {
        when(orders.get(12, "token")).thenReturn(order(7, "NEW"));
        when(random.nextNumber()).thenReturn(2);
        when(repository.insert(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(new CreatePayment(12L), admin, "token").userId()).isEqualTo(7L);
    }
    @Test void rejectsPaidOrder() {
        when(orders.get(12, "token")).thenReturn(order(7, "PAID"));
        expectStatus(() -> service.create(new CreatePayment(12L), user, "token"), HttpStatus.CONFLICT);
        verifyNoInteractions(random, repository);
    }
    @Test void rejectsDuplicateSuccess() {
        when(orders.get(12, "token")).thenReturn(order(7, "NEW"));
        when(repository.existsByOrderIdAndStatus(12L, PaymentStatus.SUCCESS)).thenReturn(true);
        expectStatus(() -> service.create(new CreatePayment(12L), user, "token"), HttpStatus.CONFLICT);
        verifyNoInteractions(random);
    }
    @Test void handlesConcurrentSuccess() {
        when(orders.get(12, "token")).thenReturn(order(7, "NEW"));
        when(random.nextNumber()).thenReturn(2);
        when(repository.insert(any(Payment.class))).thenThrow(new DuplicateKeyException("duplicate"));
        expectStatus(() -> service.create(new CreatePayment(12L), user, "token"), HttpStatus.CONFLICT);
    }
    @Test void doesNotSaveWhenExternalApiFails() {
        when(orders.get(12, "token")).thenReturn(order(7, "NEW"));
        when(random.nextNumber()).thenThrow(PaymentApiException.upstream("Unavailable"));
        expectStatus(() -> service.create(new CreatePayment(12L), user, "token"), HttpStatus.BAD_GATEWAY);
        verify(repository, never()).insert(any(Payment.class));
    }
    @Test void propagatesMissingOrderWithoutCreatingPayment() {
        when(orders.get(12, "token")).thenThrow(PaymentApiException.missing("Order"));
        expectStatus(() -> service.create(new CreatePayment(12L), user, "token"), HttpStatus.NOT_FOUND);
        verifyNoInteractions(repository, random);
    }
    @Test void getsOwnedPayment() {
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment()));
        assertThat(service.get("payment-1", user).id()).isEqualTo("payment-1");
    }
    @Test void adminGetsAnyPayment() {
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment()));
        assertThat(service.get("payment-1", admin).userId()).isEqualTo(7L);
    }
    @Test void rejectsForeignPayment() {
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment()));
        expectStatus(() -> service.get("payment-1", new PaymentAccess(8, false)), HttpStatus.FORBIDDEN);
    }
    @Test void missingPaymentIs404() {
        expectStatus(() -> service.get("missing", user), HttpStatus.NOT_FOUND);
    }
    @Test void listAlwaysScopesOrdinaryUser() {
        when(queries.find(eq(7L), eq(12L), eq(PaymentStatus.SUCCESS), any()))
                .thenReturn(new PageImpl<>(List.of(payment())));
        var result = service.list(null, 12L, PaymentStatus.SUCCESS, 0, 20, user);
        assertThat(result.content()).hasSize(1);
        verify(queries).find(eq(7L), eq(12L), eq(PaymentStatus.SUCCESS), any());
    }
    @Test void adminListsAllUsers() {
        when(queries.find(isNull(), isNull(), isNull(), any())).thenReturn(Page.empty());
        assertThat(service.list(null, null, null, 0, 20, admin).content()).isEmpty();
    }
    @Test void cannotOverrideListOwner() {
        expectStatus(() -> service.list(8L, null, null, 0, 20, user), HttpStatus.FORBIDDEN);
        verifyNoInteractions(queries);
    }
    @Test void rejectsOversizedPage() {
        expectStatus(() -> service.list(null, null, null, 0, 101, user), HttpStatus.BAD_REQUEST);
    }
    @Test void currentUserTotalUsesCallerIdentity() {
        when(queries.total(7L, now.minusSeconds(60), now, PaymentStatus.SUCCESS)).thenReturn(new BigDecimal("19.90"));
        assertThat(service.totalForCurrentUser(now.minusSeconds(60), now, PaymentStatus.SUCCESS, user).totalAmount())
                .isEqualByComparingTo("19.90");
    }
    @Test void adminTotalIsUnscoped() {
        when(queries.total(null, now.minusSeconds(60), now, null)).thenReturn(BigDecimal.ZERO);
        assertThat(service.totalForAllUsers(now.minusSeconds(60), now, null, admin).userId()).isNull();
    }
    @Test void ordinaryUserCannotGetGlobalTotal() {
        expectStatus(() -> service.totalForAllUsers(now.minusSeconds(60), now, null, user), HttpStatus.FORBIDDEN);
        verifyNoInteractions(queries);
    }
    @Test void rejectsInvalidDateRange() {
        expectStatus(() -> service.totalForCurrentUser(now, now, null, user), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(queries);
    }
    @Test void canRetryOrderWithPaymentFailedStatus() {
        when(orders.get(12,"token")).thenReturn(order(7,"PAYMENT_FAILED"));
        when(random.nextNumber()).thenReturn(2);
        when(repository.insert(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(new CreatePayment(12L),user,"token").status()).isEqualTo(PaymentStatus.SUCCESS);
    }
}
