package com.innowise.paymentservice.config;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class MongoMigrationConfig {
    @Bean
    @ConditionalOnProperty(name = "payment.migrations.enabled", havingValue = "true", matchIfMissing = true)
    InitializingBean paymentMongoMigrations(@Value("${spring.mongodb.uri}") String uri,
            @Value("${payment.migrations.change-log}") String changelog) {
        // Runs at context initialization, before the HTTP server accepts requests.
        return () -> {
            try (var resources = new ClassLoaderResourceAccessor()) {
                var database = DatabaseFactory.getInstance().openDatabase(uri, null, null, null, resources);
                try (var liquibase = new Liquibase(changelog, resources, database)) {
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            }
        };
    }
}
