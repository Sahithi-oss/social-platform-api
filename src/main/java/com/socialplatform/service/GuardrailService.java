package com.socialplatform.service;

import com.socialplatform.exception.GuardrailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Phase 2 — Atomic Locks / Guardrails.
 *
 * All three caps use atomic Redis operations so they remain
 * correct under high concurrency (200 simultaneous requests):
 *
 * 1. HORIZONTAL CAP  — INCR + compare (post:{id}:bot_count ≤ 100)
 * 2. VERTICAL CAP    — simple integer check (depthLevel ≤ 20)
 * 3. COOLDOWN CAP    — SET NX EX (cooldown:bot_{b}:human_{u}, TTL 10 min)
 *
 * Thread-safety guarantee:
 *   Redis INCR is a single atomic command on the server side.
 *   No two threads can observe the same counter value after INCR,
 *   so the bot count can never exceed 100 even under extreme concurrency.
 *   The SETNX (SET with NX flag) for the cooldown is also atomic —
 *   only one caller wins the race and sets the TTL key.
 */
@Service
public class GuardrailService {

    private static final Logger log = LoggerFactory.getLogger(GuardrailService.class);

    /** Maximum number of bot replies allowed per post */
    public static final long HORIZONTAL_CAP = 100L;

    /** Maximum thread depth allowed */
    public static final int VERTICAL_CAP = 20;

    /** Bot-to-human interaction cooldown in minutes */
    public static final long COOLDOWN_MINUTES = 10L;

    private final RedisTemplate<String, String> redisTemplate;
    private final ViralityService viralityService;

    public GuardrailService(RedisTemplate<String, String> redisTemplate,
                            ViralityService viralityService) {
        this.redisTemplate = redisTemplate;
        this.viralityService = viralityService;
    }

    // =========================================================================
    // 1. HORIZONTAL CAP — max 100 bot replies per post
    // =========================================================================

    /**
     * Atomically increments the bot comment counter for a post.
     * If the resulting count exceeds HORIZONTAL_CAP the operation is ROLLED BACK
     * (decremented) and a {@link GuardrailException} is thrown so the DB insert
     * never happens.
     *
     * <p>Using Redis INCR ensures that even 200 concurrent requests see strictly
     * sequential counter values — no two threads can both read "100" and both
     * decide they are allowed to proceed.</p>
     *
     * @param postId target post
     * @throws GuardrailException HTTP 429 when cap is exceeded
     */
    public void checkAndIncrementBotCount(Long postId) {
        String key = ViralityService.botCountKey(postId);

        // Atomic INCR — Redis serialises this on the server
        Long newCount = redisTemplate.opsForValue().increment(key);

        if (newCount == null) {
            throw new GuardrailException("Redis unavailable; cannot enforce bot cap for post " + postId);
        }

        log.debug("Bot count for post {} → {}", postId, newCount);

        if (newCount > HORIZONTAL_CAP) {
            // Roll back the increment so the counter stays accurate
            redisTemplate.opsForValue().decrement(key);
            throw new GuardrailException(
                    "Horizontal cap reached: post " + postId +
                    " already has " + HORIZONTAL_CAP + " bot replies. Request rejected (HTTP 429).");
        }
    }

    /**
     * Decrements the bot count — called if the DB insert fails after the Redis
     * increment was already applied (compensating transaction).
     */
    public void releaseBotCount(Long postId) {
        redisTemplate.opsForValue().decrement(ViralityService.botCountKey(postId));
    }

    /**
     * Returns the current number of bot replies for a post (for diagnostics).
     */
    public long getBotCount(Long postId) {
        String val = redisTemplate.opsForValue().get(ViralityService.botCountKey(postId));
        return val != null ? Long.parseLong(val) : 0L;
    }

    // =========================================================================
    // 2. VERTICAL CAP — max depth 20
    // =========================================================================

    /**
     * Validates that the requested depth level does not exceed the vertical cap.
     *
     * @param depthLevel depth provided in the request
     * @throws GuardrailException HTTP 429 when depth > 20
     */
    public void checkVerticalCap(int depthLevel) {
        if (depthLevel > VERTICAL_CAP) {
            throw new GuardrailException(
                    "Vertical cap reached: thread depth " + depthLevel +
                    " exceeds the maximum allowed depth of " + VERTICAL_CAP + ".");
        }
    }

    // =========================================================================
    // 3. COOLDOWN CAP — bot can interact with a human only once per 10 minutes
    // =========================================================================

    /**
     * Checks whether a bot is on cooldown for a specific human user.
     * Uses Redis SET NX EX (SETNX + expire in one atomic command) so that
     * only one concurrent caller can set the key — preventing race conditions
     * where two requests both see the key absent and both proceed.
     *
     * @param botId        the bot attempting the interaction
     * @param humanUserId  the human post owner
     * @throws GuardrailException HTTP 429 when cooldown is active
     */
    public void checkAndSetCooldown(Long botId, Long humanUserId) {
        String key = ViralityService.cooldownKey(botId, humanUserId);

        // SET key "1" NX EX 600  — atomic: returns true only if key did NOT exist
        Boolean wasAbsent = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", COOLDOWN_MINUTES, TimeUnit.MINUTES);

        if (Boolean.FALSE.equals(wasAbsent)) {
            // Key already existed — cooldown is active
            throw new GuardrailException(
                    "Cooldown cap: bot " + botId + " cannot interact with user " + humanUserId +
                    " more than once every " + COOLDOWN_MINUTES + " minutes.");
        }

        log.debug("Cooldown set for bot {} → user {} (TTL {} min)", botId, humanUserId, COOLDOWN_MINUTES);
    }

    /**
     * Returns true if a cooldown is currently active (useful for unit tests).
     */
    public boolean isCooldownActive(Long botId, Long humanUserId) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(ViralityService.cooldownKey(botId, humanUserId)));
    }
}
