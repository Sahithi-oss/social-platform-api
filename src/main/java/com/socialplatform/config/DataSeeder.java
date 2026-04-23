package com.socialplatform.config;

import com.socialplatform.model.Bot;
import com.socialplatform.model.User;
import com.socialplatform.repository.BotRepository;
import com.socialplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds sample Users and Bots on startup so you can test the API
 * immediately with Postman without separate setup calls.
 *
 * Only inserts if the tables are empty (idempotent on restart).
 */
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedData(UserRepository userRepository, BotRepository botRepository) {
        return args -> {
            if (userRepository.count() == 0) {
                userRepository.save(User.builder().username("alice").isPremium(true).build());
                userRepository.save(User.builder().username("bob").isPremium(false).build());
                userRepository.save(User.builder().username("charlie").isPremium(true).build());
                log.info("[SEEDER] Created 3 sample users: alice, bob, charlie");
            }

            if (botRepository.count() == 0) {
                botRepository.save(Bot.builder()
                        .name("NovaSpark")
                        .personaDescription("An enthusiastic AI that loves technology discussions.")
                        .build());
                botRepository.save(Bot.builder()
                        .name("EchoMind")
                        .personaDescription("A thoughtful AI that analyses posts and provides insightful replies.")
                        .build());
                botRepository.save(Bot.builder()
                        .name("ViraByte")
                        .personaDescription("A trending-focused AI that amplifies viral content.")
                        .build());
                log.info("[SEEDER] Created 3 sample bots: NovaSpark, EchoMind, ViraByte");
            }
        };
    }
}
