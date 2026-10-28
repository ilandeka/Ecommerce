package com.eshop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * One-shot maintenance task for the public demo; it only exists when the {@code demo-reset} profile is active.
 *
 * <p>It empties every table of the current schema (resetting the identity columns), runs the {@link DataLoader} to
 * bring back the demo users and products, and then stops the JVM. A Cloud Run <em>job</em> runs this after every deploy
 * and nightly, so whatever visitors created or broke in the live demo is thrown away.
 *
 * <p>It is a separate job (not an HTTP endpoint) on purpose: nothing on the public API can be called to wipe the
 * database.
 */
@Component
@Profile("demo-reset")
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoResetRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoResetRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataLoader dataLoader;
    private final ApplicationContext applicationContext;

    public DemoResetRunner(JdbcTemplate jdbcTemplate, DataLoader dataLoader, ApplicationContext applicationContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataLoader = dataLoader;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = current_schema()", String.class);

        if (!tables.isEmpty()) {
            String quotedTables = tables.stream()
                    .map(name -> "\"" + name.replace("\"", "\"\"") + "\"")
                    .collect(Collectors.joining(", "));
            jdbcTemplate.execute("TRUNCATE TABLE " + quotedTables + " RESTART IDENTITY CASCADE");
        }
        logger.info("Demo reset: emptied {} tables, seeding fresh demo data", tables.size());

        // DataLoader also runs by itself at startup; it only seeds an empty database, so run it again now that the
        // tables are empty.
        dataLoader.run();
        logger.info("Demo reset finished");

        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }
}
