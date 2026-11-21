package com.innowise.paymentservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.innowise.paymentservice.exception.PaymentApiException;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class OrderClient {
    private final RestClient client;
    public OrderClient(@Qualifier("orderRestClient") RestClient client) { this.client = client; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderUser(Long id) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderSnapshot(Long id, String status, BigDecimal totalPrice, OrderUser user) {}

    public OrderSnapshot get(long id, String token) {
        try {
            var order = client.get().uri("/api/v1/orders/{id}", id)
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(OrderSnapshot.class);
            if (order == null || order.id() == null || order.id() != id || order.user() == null
                    || order.user().id() == null || order.user().id() <= 0 || order.status() == null
                    || order.totalPrice() == null || order.totalPrice().signum() < 0
                    || order.totalPrice().stripTrailingZeros().scale() > 2
                    || order.totalPrice().precision() - order.totalPrice().scale() > 17) {
                throw PaymentApiException.upstream("Order Service returned invalid order data");
            }
            return order;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) throw PaymentApiException.missing("Order");
            if (ex.getStatusCode().value() == 403) throw PaymentApiException.forbidden();
            if (ex.getStatusCode().value() == 401) {
                throw new PaymentApiException(HttpStatus.UNAUTHORIZED, "Access token was rejected by Order Service");
            }
            throw PaymentApiException.upstream("Order Service is temporarily unavailable");
        } catch (RestClientException ex) {
            throw PaymentApiException.upstream("Order Service is temporarily unavailable");
        }
    }
}
