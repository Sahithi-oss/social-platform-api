package com.socialplatform.dto;

import com.socialplatform.model.AuthorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for POST /api/posts/{postId}/comments
 */
@Data
public class CreateCommentRequest {

    @NotNull(message = "authorId is required")
    private Long authorId;

    @NotNull(message = "authorType is required (USER or BOT)")
    private AuthorType authorType;

    @NotBlank(message = "content must not be blank")
    private String content;

    /**
     * Depth level of this comment in the thread.
     * 1 = root comment on a post, 2 = reply to a root comment, etc.
     * The Redis Vertical Cap rejects any depth > 20.
     */
    @Min(value = 1, message = "depthLevel must be at least 1")
    @Max(value = 20, message = "depthLevel cannot exceed 20")
    @NotNull(message = "depthLevel is required")
    private Integer depthLevel;
}
