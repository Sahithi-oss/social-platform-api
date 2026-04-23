package com.socialplatform.scheduler;

import com.socialplatform.service.ViralityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Phase 3 — CRON Sweeper.
 *
 * Runs every 5 minutes (simulating a 15-minute production sweep).
 * Scans all "user:*:pending_notifs" keys in Redis, drains each list,
 * and logs a summarised notification message.
 *
 * This task is intentionally stateless — all state lives in Redis so
 * multiple application instances can run without double-processing.
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    /** Pattern used to scan for all pending notification lists */
    private static final String PENDING_KEY_PATTERN = "user:*:pending_notifs";

    private final RedisTemplate<String, String> redisTemplate;

    public NotificationScheduler(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Fires every 5 minutes.
     * cron = "0 0/5 * * * *" → second=0, every 5 minutes, every hour, every day.
     */
    @Scheduled(cron = "0 0/5 * * * *")
    public void sweepPendingNotifications() {
        log.info("========== [CRON SWEEPER] Notification sweep started ==========");

        // Scan Redis for all pending notification list keys
        Set<String> keys = redisTemplate.keys(PENDING_KEY_PATTERN);

        if (keys == null || keys.isEmpty()) {
            log.info("[CRON SWEEPER] No pending notifications found.");
            return;
        }

        log.info("[CRON SWEEPER] Found {} user(s) with pending notifications.", keys.size());

        for (String key : keys) {
            processPendingNotificationsForKey(key);
        }

        log.info("========== [CRON SWEEPER] Notification sweep completed ==========");
    }

    /**
     * Atomically drains all pending notifications for a single user using
     * leftPop — no separate delete() needed, so there is no race condition
     * window where a new notification could be pushed between the read and
     * the delete.
     *
     * @param key  the Redis list key, e.g. "user:42:pending_notifs"
     */
    private void processPendingNotificationsForKey(String key) {
        // Extract userId from key pattern "user:{id}:pending_notifs"
        String userId = key.split(":")[1];

        // Atomically pop ALL messages one by one.
        // leftPop returns null when the list is empty, so the loop stops cleanly.
        // No separate delete() call needed — the list empties itself.
        List<String> messages = new ArrayList<>();
        String msg;
        while ((msg = redisTemplate.opsForList().leftPop(key)) != null) {
            messages.add(msg);
        }

        if (messages.isEmpty()) {
            return;
        }

        // Build summarised message
        String firstMessage = messages.get(0);
        int totalCount = messages.size();

        String summary;
        if (totalCount == 1) {
            summary = firstMessage;
        } else {
            // Extract bot name from first message format: "BotName replied to your post #X"
            String botName = firstMessage.split(" ")[0];
            int others = totalCount - 1;
            summary = "Summarized Push Notification: " + botName +
                      " and [" + others + "] others interacted with your posts.";
        }

        log.info("[SUMMARIZED PUSH NOTIFICATION] → User {}: \"{}\"", userId, summary);
    }
}