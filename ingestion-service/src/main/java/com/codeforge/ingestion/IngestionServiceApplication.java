package com.codeforge.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
public class IngestionServiceApplication {
    public static void main(String[] args) {
        // Pin the JVM timezone before the datasource initializes. Some JVMs default
        // to the legacy alias "Asia/Calcutta", which PostgreSQL rejects on connect
        // ("invalid value for parameter TimeZone"), crashing startup. Setting it here
        // makes the packaged jar self-contained (no -Duser.timezone launch arg needed).
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(IngestionServiceApplication.class, args);
    }
}