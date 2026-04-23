package com.socialplatform.controller;

import com.socialplatform.dto.ApiResponse;
import com.socialplatform.dto.CreateCommentRequest;
import com.socialplatform.dto.CreatePostRequest;
import com.socialplatform.dto.LikePostRequest;
import com.socialplatform.model.Comment;
import com.socialplatform.model.Post;
import com.socialplatform.service.PostService;
import com.socialplatform.service.ViralityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Phase 1 — Standard REST Endpoints.
 *
 * POST /api/posts                    — Create a new post
 * POST /api/posts/{postId}/comments  — Add a comment (with guardrails for bots)
 * POST /api/posts/{postId}/like      — Like a post (human only)
 * GET  /api/posts/{postId}/virality  — Inspect real-time virality score (bonus)
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService     postService;
    private final ViralityService viralityService;

    public PostController(PostService postService, ViralityService viralityService) {
        this.postService     = postService;
        this.viralityService = viralityService;
    }

    // -------------------------------------------------------------------------
    // POST /api/posts
    // -------------------------------------------------------------------------
    @PostMapping
    public ResponseEntity<ApiResponse<Post>> createPost(
            @Valid @RequestBody CreatePostRequest request) {

        Post post = postService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Post created successfully.", post));
    }

    // -------------------------------------------------------------------------
    // POST /api/posts/{postId}/comments
    // -------------------------------------------------------------------------
    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<Comment>> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request) {

        Comment comment = postService.addComment(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Comment added successfully.", comment));
    }

    // -------------------------------------------------------------------------
    // POST /api/posts/{postId}/like
    // -------------------------------------------------------------------------
    @PostMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<Post>> likePost(
            @PathVariable Long postId,
            @Valid @RequestBody LikePostRequest request) {

        Post post = postService.likePost(postId, request.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Post liked successfully.", post));
    }

    // -------------------------------------------------------------------------
    // GET /api/posts/{postId}/virality  (bonus diagnostic endpoint)
    // -------------------------------------------------------------------------
    @GetMapping("/{postId}/virality")
    public ResponseEntity<ApiResponse<Long>> getVirality(@PathVariable Long postId) {
        long score = viralityService.getViralityScore(postId);
        return ResponseEntity.ok(ApiResponse.ok("Virality score retrieved.", score));
    }
}
