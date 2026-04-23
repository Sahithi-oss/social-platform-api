package com.socialplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Phase 2 — Redis Virality Engine.
 *
 * Manages real-time virality scoring per post.
 * All operations are atomic Redis operations — no Java state.
 *
 * Scoring weights:
 *  - Bot Reply       → +1
 *  - Human Like      → +20
 *  - Human Comment   → +50
 */
@Service
public class ViralityService {

    private static final Logger log = LoggerFactory.getLogger(ViralityService.class);

    // Virality score weights
    public static final long POINTS_BOT_REPLY      = 1L;
    public static final long POINTS_HUMAN_LIKE     = 20L;
    public static final long POINTS_HUMAN_COMMENT  = 50L;

    private final RedisTemplate<String, String> redisTemplate;

    public ViralityService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // -------------------------------------------------------------------------
    // Key builders
    // -------------------------------------------------------------------------

    /** Redis key: virality score for a post  →  "post:{id}:virality_score" */
    public static String viralityKey(Long postId) {
        return "post:" + postId + ":virality_score";
    }

    /** Redis key: bot reply counter for a post  →  "post:{id}:bot_count" */
    public static String botCountKey(Long postId) {
        return "post:" + postId + ":bot_count";
    }

    /** Redis key: cooldown between a bot and a human user  →  "cooldown:bot_{b}:human_{u}" */
    public static String cooldownKey(Long botId, Long humanUserId) {
        return "cooldown:bot_" + botId + ":human_" + humanUserId;
    }

    /** Redis key: notification cooldown for a user  →  "notif:cooldown:user_{id}" */
    public static String notifCooldownKey(Long userId) {
        return "notif:cooldown:user_" + userId;
    }

    /** Redis key: pending notification list for a user  →  "user:{id}:pending_notifs" */
    public static String pendingNotifsKey(Long userId) {
        return "user:" + userId + ":pending_notifs";
    }

    // -------------------------------------------------------------------------
    // Virality scoring
    // -------------------------------------------------------------------------

    /**
     * Atomically increments the virality score of a post in Redis.
     *
     * @param postId the target post
     * @param points positive long value to add
     * @return the new score after increment
     */
    public long incrementVirality(Long postId, long points) {
        Long newScore = redisTemplate.opsForValue().increment(viralityKey(postId), points);
        log.debug("Virality score for post {} incremented by {} → new score = {}", postId, points, newScore);
        return newScore != null ? newScore : 0L;
    }

    /** Convenience: record a bot reply (+1) */
    public long recordBotReply(Long postId) {
        return incrementVirality(postId, POINTS_BOT_REPLY);
    }

    /** Convenience: record a human like (+20) */
    public long recordHumanLike(Long postId) {
        return incrementVirality(postId, POINTS_HUMAN_LIKE);
    }

    /** Convenience: record a human comment (+50) */
    public long recordHumanComment(Long postId) {
        return incrementVirality(postId, POINTS_HUMAN_COMMENT);
    }

    /**
     * Retrieve the current virality score from Redis.
     * Returns 0 if the key has not been set yet.
     */
    public long getViralityScore(Long postId) {
        String val = redisTemplate.opsForValue().get(viralityKey(postId));
        return val != null ? Long.parseLong(val) : 0L;
    }
}
