package com.socialplatform.service;

import com.socialplatform.dto.CreateCommentRequest;
import com.socialplatform.dto.CreatePostRequest;
import com.socialplatform.exception.GuardrailException;
import com.socialplatform.exception.ResourceNotFoundException;
import com.socialplatform.model.AuthorType;
import com.socialplatform.model.Bot;
import com.socialplatform.model.Comment;
import com.socialplatform.model.Post;
import com.socialplatform.model.User;
import com.socialplatform.repository.BotRepository;
import com.socialplatform.repository.CommentRepository;
import com.socialplatform.repository.PostRepository;
import com.socialplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core business logic for Posts and Comments.
 *
 * Data flow for a BOT comment (concurrency-safe):
 *  1. Validate depth cap (pure check, no Redis write)
 *  2. Validate cooldown (atomic SETNX)
 *  3. Atomically increment bot_count via Redis INCR → reject if > 100
 *  4. Persist to PostgreSQL (only if steps 1-3 pass)
 *  5. On DB failure → compensate by decrementing bot_count in Redis
 *  6. Update virality score
 *  7. Trigger notification engine
 *
 * This ordering guarantees the DB never has > 100 bot comments per post,
 * even under 200 simultaneous requests.
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostRepository       postRepository;
    private final CommentRepository    commentRepository;
    private final UserRepository       userRepository;
    private final BotRepository        botRepository;
    private final GuardrailService     guardrailService;
    private final ViralityService      viralityService;
    private final NotificationService  notificationService;

    public PostService(PostRepository postRepository,
                       CommentRepository commentRepository,
                       UserRepository userRepository,
                       BotRepository botRepository,
                       GuardrailService guardrailService,
                       ViralityService viralityService,
                       NotificationService notificationService) {
        this.postRepository      = postRepository;
        this.commentRepository   = commentRepository;
        this.userRepository      = userRepository;
        this.botRepository       = botRepository;
        this.guardrailService    = guardrailService;
        this.viralityService     = viralityService;
        this.notificationService = notificationService;
    }

    // =========================================================================
    // POST /api/posts — Create a new post
    // =========================================================================

    @Transactional
    public Post createPost(CreatePostRequest req) {
        // Validate author exists
        validateAuthorExists(req.getAuthorId(), req.getAuthorType());

        Post post = Post.builder()
                .authorId(req.getAuthorId())
                .authorType(req.getAuthorType())
                .content(req.getContent())
                .likeCount(0)
                .build();

        Post saved = postRepository.save(post);
        log.info("Post created: id={} by {} {}", saved.getId(), req.getAuthorType(), req.getAuthorId());
        return saved;
    }

    // =========================================================================
    // POST /api/posts/{postId}/comments — Add a comment
    // =========================================================================

    /**
     * Adds a comment to a post.
     *
     * For BOT authors, all three Redis guardrails are enforced BEFORE persisting:
     *   1. Vertical cap (depth ≤ 20)
     *   2. Cooldown cap (bot–human once per 10 min)
     *   3. Horizontal cap (≤ 100 bots per post) — atomic INCR
     */
    @Transactional
    public Comment addComment(Long postId, CreateCommentRequest req) {
        // 1. Verify post exists
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        // 2. Verify author exists
        validateAuthorExists(req.getAuthorId(), req.getAuthorType());

        boolean isBotAuthor = req.getAuthorType() == AuthorType.BOT;

        if (isBotAuthor) {
            // ---- Phase 2 Guardrails ----

            // 2a. VERTICAL CAP — reject depth > 20
            guardrailService.checkVerticalCap(req.getDepthLevel());

            // 2b. COOLDOWN CAP — find post owner (must be a human) and check cooldown
            if (post.getAuthorType() == AuthorType.USER) {
                guardrailService.checkAndSetCooldown(req.getAuthorId(), post.getAuthorId());
            }

            // 2c. HORIZONTAL CAP — atomic INCR; rolls back on its own if limit hit
            guardrailService.checkAndIncrementBotCount(postId);
        }

        // 3. Persist to PostgreSQL
        Comment comment;
        try {
            comment = Comment.builder()
                    .post(post)
                    .authorId(req.getAuthorId())
                    .authorType(req.getAuthorType())
                    .content(req.getContent())
                    .depthLevel(req.getDepthLevel())
                    .build();
            comment = commentRepository.save(comment);
        } catch (Exception ex) {
            // Compensating transaction: roll back Redis bot count if DB fails
            if (isBotAuthor) {
                guardrailService.releaseBotCount(postId);
                log.error("DB insert failed; released Redis bot count for post {}", postId);
            }
            throw ex;
        }

        // 4. Update virality score
        if (isBotAuthor) {
            long score = viralityService.recordBotReply(postId);
            log.info("Bot reply virality → post {} score = {}", postId, score);

            // 5. Trigger notification if post owner is a human
            if (post.getAuthorType() == AuthorType.USER) {
                Bot bot = botRepository.findById(req.getAuthorId())
                        .orElseThrow(() -> new ResourceNotFoundException("Bot not found: " + req.getAuthorId()));
                notificationService.handleBotInteraction(bot.getName(), post.getAuthorId(), postId);
            }
        } else {
            long score = viralityService.recordHumanComment(postId);
            log.info("Human comment virality → post {} score = {}", postId, score);
        }

        log.info("Comment created: id={} on post={} by {} {}", comment.getId(), postId,
                req.getAuthorType(), req.getAuthorId());
        return comment;
    }

    // =========================================================================
    // POST /api/posts/{postId}/like — Like a post
    // =========================================================================

    @Transactional
    public Post likePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        // Validate user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        post.setLikeCount(post.getLikeCount() + 1);
        Post updated = postRepository.save(post);

        // Update virality
        long score = viralityService.recordHumanLike(postId);
        log.info("Like recorded → post {} likeCount={} viralityScore={}", postId, updated.getLikeCount(), score);

        return updated;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Validates that the given author id exists in the correct table.
     * Throws {@link ResourceNotFoundException} if not found.
     */
    private void validateAuthorExists(Long authorId, AuthorType authorType) {
        if (authorType == AuthorType.USER) {
            userRepository.findById(authorId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authorId));
        } else {
            botRepository.findById(authorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bot not found: " + authorId));
        }
    }
}
