package com.innowise.paymentservice.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.innowise.paymentservice.entity.*;
import com.innowise.paymentservice.repository.PaymentRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentHttpIT {
    static final WireMockServer upstream = new WireMockServer(0);
    static final RSAKey key;
    static MongoDBContainer container;
    static final String mongoUri;
    static {
        try { key = new RSAKeyGenerator(2048).keyID("payment-test-key").generate(); }
        catch (JOSEException ex) { throw new ExceptionInInitializerError(ex); }
        upstream.start();
        // Override is only for isolated test databases when Docker is unavailable.
        String external = System.getProperty("payment.test.mongo-uri");
        if (external == null || external.isBlank()) {
            container = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
            container.start(); mongoUri = container.getReplicaSetUrl();
        } else { mongoUri = external; }
        jwks();
    }
    static void jwks() {
        upstream.stubFor(get(urlEqualTo("/.well-known/jwks.json"))
                .willReturn(okJson(new JWKSet(key.toPublicJWK()).toString())));
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> mongoUri);
        registry.add("payment.order-service-url", upstream::baseUrl);
        registry.add("payment.random-api-url", upstream::baseUrl);
        registry.add("payment.jwt.jwk-set-uri", () -> upstream.baseUrl() + "/.well-known/jwks.json");
    }
    @LocalServerPort int port;
    @Autowired PaymentRepository repository;
    @Autowired MongoTemplate mongo;
    @Autowired ObjectMapper json;
    final HttpClient http = HttpClient.newHttpClient();
    final Instant start = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach void reset() { repository.deleteAll(); upstream.resetAll(); jwks(); }
    @AfterAll static void stop() { upstream.stop(); if (container != null) container.stop(); }

    String token(long userId, String role, String type) throws Exception {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder().subject(Long.toString(userId)).claim("user_id", userId)
                .claim("role", role).claim("tokenType", type).issuer("authentication-service")
                .audience("java-advanced-api").jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).build();
        var signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        signed.sign(new RSASSASigner(key)); return signed.serialize();
    }
    HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    void order(long id, long owner, String amount) {
        upstream.stubFor(get(urlEqualTo("/api/v1/orders/" + id)).willReturn(okJson(
                "{\"id\":" + id + ",\"status\":\"NEW\",\"totalPrice\":" + amount + ",\"user\":{\"id\":" + owner + "}}")));
    }
    void number(int value) { upstream.stubFor(get(urlPathEqualTo("/integers/")).willReturn(ok(Integer.toString(value)))); }
    Payment seed(long orderId, long owner, String amount, PaymentStatus status, Instant time) {
        var p = new Payment(); p.setId(UUID.randomUUID().toString()); p.setOrderId(orderId); p.setUserId(owner);
        p.setPaymentAmount(new BigDecimal(amount)); p.setStatus(status); p.setTimestamp(time);
        return repository.insert(p);
    }

    @Test void fullCreateReadFlowAndRepeatProtection() throws Exception {
        order(12, 7, "19.90"); number(42); String token = token(7,"USER","access");
        var created = request("POST", "/api/v1/payments", "{\"orderId\":12}", token);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode result = json.readTree(created.body());
        assertThat(result.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.get("userId").asLong()).isEqualTo(7);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(request("GET", "/api/v1/payments/" + result.get("id").asText(), null, token).statusCode()).isEqualTo(200);
        assertThat(request("POST", "/api/v1/payments", "{\"orderId\":12}", token).statusCode()).isEqualTo(409);
        upstream.verify(1, getRequestedFor(urlPathEqualTo("/integers/")));
    }
    @Test void failedAttemptCanBeRetried() throws Exception {
        order(12,7,"19.90"); number(3); String token = token(7,"USER","access");
        var first = request("POST", "/api/v1/payments", "{\"orderId\":12}", token);
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(json.readTree(first.body()).get("status").asText()).isEqualTo("FAILED");
        number(2);
        assertThat(request("POST", "/api/v1/payments", "{\"orderId\":12}", token).statusCode()).isEqualTo(201);
        assertThat(repository.count()).isEqualTo(2);
    }
    @Test void filtersNeverExposeAnotherUsersPayments() throws Exception {
        var other = seed(13,8,"90.00",PaymentStatus.SUCCESS,start);
        seed(12,7,"19.90",PaymentStatus.FAILED,start);
        String token = token(7,"USER","access");
        var own = request("GET", "/api/v1/payments?status=FAILED", null, token);
        assertThat(own.statusCode()).isEqualTo(200);
        assertThat(json.readTree(own.body()).get("totalElements").asLong()).isEqualTo(1);
        assertThat(json.readTree(request("GET", "/api/v1/payments?orderId=13", null, token).body()).get("totalElements").asLong()).isZero();
        assertThat(request("GET", "/api/v1/payments?userId=8", null, token).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/v1/payments/" + other.getId(), null, token).statusCode()).isEqualTo(403);
    }
    @Test void sumsDecimalsAndUsesInclusiveFromExclusiveTo() throws Exception {
        seed(1,7,"0.10",PaymentStatus.SUCCESS,start);
        seed(2,7,"0.20",PaymentStatus.SUCCESS,start.plusSeconds(1));
        seed(3,7,"9.00",PaymentStatus.FAILED,start.plusSeconds(1));
        seed(4,8,"1.00",PaymentStatus.SUCCESS,start.plusSeconds(1));
        seed(5,7,"99.00",PaymentStatus.SUCCESS,start.plusSeconds(2));
        String params = "?from=2026-01-01T00:00:00Z&to=2026-01-01T00:00:02Z&status=SUCCESS";
        var own = request("GET", "/api/v1/payments/total" + params, null, token(7,"USER","access"));
        assertThat(own.statusCode()).isEqualTo(200);
        assertThat(new BigDecimal(json.readTree(own.body()).get("totalAmount").asText())).isEqualByComparingTo("0.30");
        var all = request("GET", "/api/v1/payments/total/all" + params, null, token(1,"ADMIN","access"));
        assertThat(all.statusCode()).isEqualTo(200);
        assertThat(new BigDecimal(json.readTree(all.body()).get("totalAmount").asText())).isEqualByComparingTo("1.30");
        var attempts = request("GET", "/api/v1/payments/total" + params.replace("&status=SUCCESS", ""), null, token(7,"USER","access"));
        assertThat(new BigDecimal(json.readTree(attempts.body()).get("totalAmount").asText())).isEqualByComparingTo("9.30");
    }
    @Test void adminCanFilterAnotherUser() throws Exception {
        seed(1,7,"1.00",PaymentStatus.SUCCESS,start); seed(2,8,"2.00",PaymentStatus.FAILED,start);
        var response = request("GET", "/api/v1/payments?userId=8", null, token(1,"ADMIN","access"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("content").get(0).get("userId").asLong()).isEqualTo(8);
    }
    @Test void securityAndInputValidation() throws Exception {
        assertThat(request("GET", "/api/v1/payments",null,null).statusCode()).isEqualTo(401);
        assertThat(request("GET", "/api/v1/payments",null,token(7,"USER","refresh")).statusCode()).isEqualTo(401);
        String token=token(7,"USER","access");
        assertThat(request("POST","/api/v1/payments","{\"orderId\":-1}",token).statusCode()).isEqualTo(400);
        assertThat(request("GET","/api/v1/payments?status=BOGUS",null,token).statusCode()).isEqualTo(400);
        assertThat(request("GET","/api/v1/payments?size=101",null,token).statusCode()).isEqualTo(400);
        String dates="?from=2026-01-01T00:00:00Z&to=2026-01-01T00:00:00Z";
        assertThat(request("GET","/api/v1/payments/total"+dates,null,token).statusCode()).isEqualTo(400);
        assertThat(request("GET","/api/v1/payments/total/all"+dates,null,token).statusCode()).isEqualTo(403);
    }
    @Test void providerFailureDoesNotCreateFakeFailedPayment() throws Exception {
        order(12,7,"19.90"); upstream.stubFor(get(urlPathEqualTo("/integers/")).willReturn(serverError()));
        assertThat(request("POST","/api/v1/payments","{\"orderId\":12}",token(7,"USER","access")).statusCode()).isEqualTo(502);
        assertThat(repository.count()).isZero();
    }
    @Test void verifiesLiquibaseCollectionAndIndexes() {
        assertThat(mongo.collectionExists("payments")).isTrue();
        var indexes = mongo.getCollection("payments").listIndexes().into(new ArrayList<>());
        assertThat(indexes).anySatisfy(i -> {
            assertThat(i.getString("name")).isEqualTo("payments_one_success_per_order");
            assertThat(i.getBoolean("unique")).isTrue();
        });
        assertThat(mongo.getCollection("DATABASECHANGELOG").countDocuments()).isEqualTo(2);
        seed(12,7,"1.00",PaymentStatus.SUCCESS,start);
        assertThatThrownBy(() -> seed(12,7,"2.00",PaymentStatus.SUCCESS,start))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        Document raw = mongo.getCollection("payments").find().first();
        assertThat(raw.get("payment_amount")).isInstanceOf(org.bson.types.Decimal128.class);
    }
}
