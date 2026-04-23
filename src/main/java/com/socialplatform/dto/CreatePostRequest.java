package com.socialplatform.dto;

import com.socialplatform.model.AuthorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for POST /api/posts
 */
@Data
public class CreatePostRequest {

    @NotNull(message = "authorId is required")
    private Long authorId;

    @NotNull(message = "authorType is required (USER or BOT)")
    private AuthorType authorType;

    @NotBlank(message = "content must not be blank")
    private String content;
}
