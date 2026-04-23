package com.socialplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Phase 3 — Smart Notification Batching.
 *
 * When a bot interacts with a user's post:
 *   • If the user HAS already received a notification in the last 15 min
 *     → push the notification string into a Redis List (pending queue).
 *   • If the user has NOT received a notification recently
 *     → log "Push Notification Sent" and set a 15-minute cooldown key.
 *
 * The CRON sweeper (@NotificationScheduler) drains the pending queues
 * every 5 minutes and logs a summarised message.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Cooldown window before grouping starts (minutes) */
    private static final long NOTIF_COOLDOWN_MINUTES = 15L;

    private final RedisTemplate<String, String> redisTemplate;

    public NotificationService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Handles a bot interaction with a human user's post/comment.
     *
     * @param botName      display name of the interacting bot
     * @param humanUserId  the human who owns the content the bot interacted with
     * @param postId       the post involved (for message context)
     */
    public void handleBotInteraction(String botName, Long humanUserId, Long postId) {
        String cooldownKey = ViralityService.notifCooldownKey(humanUserId);
        String pendingKey  = ViralityService.pendingNotifsKey(humanUserId);

        String notifMessage = botName + " replied to your post #" + postId;

        Boolean cooldownActive = redisTemplate.hasKey(cooldownKey);

        if (Boolean.TRUE.equals(cooldownActive)) {
            // User already got a push notification recently → batch it
            redisTemplate.opsForList().rightPush(pendingKey, notifMessage);
            log.info("[NOTIF QUEUED] User {} → pending queue: \"{}\"", humanUserId, notifMessage);
        } else {
            // No recent notification → send immediately and set cooldown
            log.info("[PUSH NOTIFICATION SENT] to User {}: \"{}\"", humanUserId, notifMessage);
            redisTemplate.opsForValue().set(cooldownKey, "1", NOTIF_COOLDOWN_MINUTES, TimeUnit.MINUTES);
        }
    }

    /**
     * Returns the number of pending notifications for a user (for diagnostics).
     */
    public long getPendingCount(Long userId) {
        Long size = redisTemplate.opsForList().size(ViralityService.pendingNotifsKey(userId));
        return size != null ? size : 0L;
    }
}
