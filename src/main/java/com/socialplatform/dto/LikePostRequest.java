package com.socialplatform.dto;

import com.socialplatform.model.AuthorType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for POST /api/posts/{postId}/like
 */
@Data
public class LikePostRequest {

    @NotNull(message = "userId is required")
    private Long userId;
}
