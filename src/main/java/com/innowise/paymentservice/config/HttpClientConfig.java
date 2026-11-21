package com.innowise.paymentservice.config;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {
    @Bean
    Clock paymentClock() { return Clock.systemUTC(); }

    @Bean
    RestClient orderRestClient(@Value("${payment.order-service-url}") String url,
            @Value("${payment.http.connect-timeout}") Duration connect,
            @Value("${payment.http.read-timeout}") Duration read) { return client(url, connect, read); }

    @Bean
    RestClient randomRestClient(@Value("${payment.random-api-url}") String url,
            @Value("${payment.http.connect-timeout}") Duration connect,
            @Value("${payment.http.read-timeout}") Duration read) { return client(url, connect, read); }

    private RestClient client(String url, Duration connect, Duration read) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
}
