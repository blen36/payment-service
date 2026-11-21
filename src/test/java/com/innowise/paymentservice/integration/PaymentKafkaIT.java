package com.innowise.paymentservice.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.innowise.paymentservice.messaging.*;
import com.innowise.paymentservice.repository.PaymentRepository;
import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties={"payment.events.enabled=true","payment.events.scheduling-enabled=false"})
@AutoConfigureMockMvc
class PaymentKafkaIT {
    @Container static MongoDBContainer mongo=new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
    @Container static KafkaContainer broker=new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));
    static final WireMockServer upstream=new WireMockServer(0);
    static {upstream.start();}
    @DynamicPropertySource static void settings(DynamicPropertyRegistry r) {
        r.add("spring.mongodb.uri",mongo::getReplicaSetUrl);
        r.add("spring.kafka.bootstrap-servers",broker::getBootstrapServers);
        r.add("payment.order-service-url",upstream::baseUrl);r.add("payment.random-api-url",upstream::baseUrl);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PaymentRepository payments;
    @Autowired PaymentEventPublisher publisher;
    @BeforeEach void reset() {payments.deleteAll();upstream.resetAll();}
    @AfterAll static void stop() {upstream.stop();}
    String create(int number) throws Exception {
        upstream.stubFor(get(urlEqualTo("/api/v1/orders/12")).willReturn(okJson("{\"id\":12,\"status\":\"NEW\",\"totalPrice\":19.90,\"user\":{\"id\":7}}")));
        upstream.stubFor(get(urlPathEqualTo("/integers/")).willReturn(ok(Integer.toString(number))));
        String body=mvc.perform(post("/api/v1/payments").contentType("application/json").content("{\"orderId\":12}")
                .with(jwt().jwt(j->j.subject("7").claim("role","USER")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }
    KafkaConsumer<String,String> consumer() {
        var props=new Properties();props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,broker.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG,"verify-"+UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,"false");
        var consumer=new KafkaConsumer<>(props,new StringDeserializer(),new StringDeserializer());consumer.subscribe(List.of("payment-events"));return consumer;
    }
    void assertEvent(String id,String status) {
        try(var consumer=consumer()) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(()-> {
                boolean found=false;
                for(var record:consumer.poll(Duration.ofMillis(500))) {
                    var event=json.readValue(record.value(),PaymentEvent.class);
                    if(event.eventId().equals(id)) {event.validate();assertThat(event.status()).isEqualTo(status);assertThat(record.key()).isEqualTo("12");assertThat(event.paymentAmount()).isEqualByComparingTo("19.90");found=true;}
                }
                assertThat(found).isTrue();
            });
        }
    }
    @Test void successFlowsFromHttpThroughMongoToKafka() throws Exception {
        String id=create(2);assertThat(payments.findById(id).orElseThrow().isEventPublished()).isFalse();
        publisher.publishPending();assertThat(payments.findById(id).orElseThrow().isEventPublished()).isTrue();assertEvent(id,"SUCCESS");
    }
    @Test void failureAlsoProducesEvent() throws Exception {String id=create(3);publisher.publishPending();assertEvent(id,"FAILED");}
    @Test void brokerOutageLeavesDurableEventAndRetryPublishesIt() throws Exception {
        String id=create(2);
        broker.getDockerClient().pauseContainerCmd(broker.getContainerId()).exec();
        try {publisher.publishPending();assertThat(payments.findById(id).orElseThrow().isEventPublished()).isFalse();}
        finally {broker.getDockerClient().unpauseContainerCmd(broker.getContainerId()).exec();}
        await().atMost(Duration.ofSeconds(40)).untilAsserted(()-> {publisher.publishPending();assertThat(payments.findById(id).orElseThrow().isEventPublished()).isTrue();});
        assertEvent(id,"SUCCESS");
    }
}
