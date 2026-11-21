package com.innowise.paymentservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "payment.events.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentKafkaConfig {
    @Bean
    NewTopic paymentEventsTopic(@Value("${payment.events.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "payment.events.scheduling-enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {}
}
