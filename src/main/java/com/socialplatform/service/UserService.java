package com.socialplatform.service;

import com.socialplatform.model.Bot;
import com.socialplatform.model.User;
import com.socialplatform.repository.BotRepository;
import com.socialplatform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed / management service for Users and Bots.
 * Exposes simple create methods used by the seeder and optional REST endpoints.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final BotRepository  botRepository;

    public UserService(UserRepository userRepository, BotRepository botRepository) {
        this.userRepository = userRepository;
        this.botRepository  = botRepository;
    }

    @Transactional
    public User createUser(String username, boolean isPremium) {
        User user = User.builder()
                .username(username)
                .isPremium(isPremium)
                .build();
        return userRepository.save(user);
    }

    @Transactional
    public Bot createBot(String name, String personaDescription) {
        Bot bot = Bot.builder()
                .name(name)
                .personaDescription(personaDescription)
                .build();
        return botRepository.save(bot);
    }
}
