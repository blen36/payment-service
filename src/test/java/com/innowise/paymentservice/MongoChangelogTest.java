package com.innowise.paymentservice;

import liquibase.changelog.ChangeLogParameters;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MongoChangelogTest {
    @Test void parsesMongoExtensionChangesWithoutJdbc() throws Exception {
        try (var resources = new ClassLoaderResourceAccessor()) {
            String file = "db/changelog/db.changelog-master.xml";
            var changelog = ChangeLogParserFactory.getInstance().getParser(file, resources)
                    .parse(file, new ChangeLogParameters(), resources);
            assertThat(changelog.getChangeSets()).hasSize(3);
            assertThat(changelog.getChangeSets().getFirst().getChanges().getFirst().getClass().getName())
                    .contains("liquibase.ext.mongodb");
        }
    }
}
