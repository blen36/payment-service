package com.innowise.paymentservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean(initMethod = "start", destroyMethod = "stop")
    MongoDBContainer mongoDbContainer() { return new MongoDBContainer(DockerImageName.parse("mongo:8.0")); }

    @Bean(initMethod = "start", destroyMethod = "stop")
    KafkaContainer kafkaContainer() { return new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0")); }

    @Bean
    DynamicPropertyRegistrar kafkaProperties(KafkaContainer kafka) {
        return registry -> registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Bean
    DynamicPropertyRegistrar mongoProperties(MongoDBContainer mongo) {
        return registry -> registry.add("spring.mongodb.uri", mongo::getReplicaSetUrl);
    }
}
