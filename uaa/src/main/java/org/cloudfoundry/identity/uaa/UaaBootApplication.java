package org.cloudfoundry.identity.uaa;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@SpringBootApplication
@EnableWebSecurity
public class UaaBootApplication {
    public static void main(String... args) {
        SpringApplication.run(UaaBootApplication.class, args);
    }

}

