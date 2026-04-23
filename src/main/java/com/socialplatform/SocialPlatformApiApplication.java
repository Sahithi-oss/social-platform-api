package com.socialplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Social Platform API microservice.
 * Enables scheduling for the CRON-based notification sweeper.
 */
@SpringBootApplication
@EnableScheduling
public class SocialPlatformApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialPlatformApiApplication.class, args);
    }
}
