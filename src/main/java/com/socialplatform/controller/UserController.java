package com.socialplatform.controller;

import com.socialplatform.dto.ApiResponse;
import com.socialplatform.model.Bot;
import com.socialplatform.model.User;
import com.socialplatform.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Utility endpoints to quickly seed Users and Bots for testing.
 *
 * POST /api/users       — Create a user
 * POST /api/bots        — Create a bot
 */
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/api/users")
    public ResponseEntity<ApiResponse<User>> createUser(
            @RequestParam String username,
            @RequestParam(defaultValue = "false") boolean isPremium) {

        User user = userService.createUser(username, isPremium);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created.", user));
    }

    @PostMapping("/api/bots")
    public ResponseEntity<ApiResponse<Bot>> createBot(
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "A helpful AI assistant") String personaDescription) {

        Bot bot = userService.createBot(name, personaDescription);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Bot created.", bot));
    }
}
