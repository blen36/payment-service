package com.innowise.paymentservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.innowise.paymentservice.exception.PaymentApiException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class ExternalClientsTest {
    static WireMockServer server;
    RandomNumberClient random;
    OrderClient orders;
    @BeforeAll static void start() { server = new WireMockServer(0); server.start(); }
    @AfterAll static void stop() { server.stop(); }
    @BeforeEach void setup() {
        server.resetAll();
        var factory = new SimpleClientHttpRequestFactory(); factory.setReadTimeout(1000); factory.setConnectTimeout(1000);
        var rest = RestClient.builder().baseUrl(server.baseUrl()).requestFactory(factory).build();
        random = new RandomNumberClient(rest); orders = new OrderClient(rest);
    }
    @Test void parsesNumberAndSendsExpectedParameters() {
        server.stubFor(get(urlPathEqualTo("/integers/")).willReturn(ok("42\n")));
        assertThat(random.nextNumber()).isEqualTo(42);
        server.verify(getRequestedFor(urlPathEqualTo("/integers/")).withQueryParam("num", equalTo("1"))
                .withQueryParam("min", equalTo("1")).withQueryParam("max", equalTo("100"))
                .withQueryParam("format", equalTo("plain")));
    }
    @ParameterizedTest @ValueSource(strings = {"", "Error: quota exhausted", "0", "101", "2 3"})
    void rejectsMalformedNumbers(String body) {
        server.stubFor(get(urlPathEqualTo("/integers/")).willReturn(ok(body)));
        assertThatThrownBy(random::nextNumber).isInstanceOfSatisfying(PaymentApiException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
    @Test void handlesProviderHttpError() {
        server.stubFor(get(urlPathEqualTo("/integers/")).willReturn(serverError()));
        assertThatThrownBy(random::nextNumber).isInstanceOf(PaymentApiException.class);
    }
    @Test void handlesTimeout() {
        server.stubFor(get(urlPathEqualTo("/integers/")).willReturn(ok("2").withFixedDelay(2000)));
        assertThatThrownBy(random::nextNumber).isInstanceOf(PaymentApiException.class);
    }
    @Test void readsOrderShapeUsedByExistingOrderServiceAndForwardsToken() {
        server.stubFor(get(urlEqualTo("/api/v1/orders/12")).willReturn(okJson("""
                {"id":12,"status":"NEW","totalPrice":19.90,"items":[],"user":{"id":7,"email":"u@example.test"}}
                """)));
        var order = orders.get(12, "access-token");
        assertThat(order.user().id()).isEqualTo(7);
        assertThat(order.totalPrice()).isEqualByComparingTo("19.90");
        server.verify(getRequestedFor(urlEqualTo("/api/v1/orders/12")).withHeader("Authorization", equalTo("Bearer access-token")));
    }
    @ParameterizedTest @ValueSource(ints = {401, 403, 404})
    void preservesOrderAccessErrors(int status) {
        server.stubFor(get(urlEqualTo("/api/v1/orders/12")).willReturn(aResponse().withStatus(status)));
        assertThatThrownBy(() -> orders.get(12,"token")).isInstanceOfSatisfying(PaymentApiException.class,
                e -> assertThat(e.status().value()).isEqualTo(status));
    }
    @Test void refusesInvalidOrderData() {
        server.stubFor(get(urlEqualTo("/api/v1/orders/12")).willReturn(okJson("{\"id\":12,\"status\":\"NEW\"}")));
        assertThatThrownBy(() -> orders.get(12,"token")).isInstanceOfSatisfying(PaymentApiException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
