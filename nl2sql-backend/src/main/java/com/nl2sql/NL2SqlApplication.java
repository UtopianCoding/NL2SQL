package com.nl2sql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
public class NL2SqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(NL2SqlApplication.class, args);
    }
}
