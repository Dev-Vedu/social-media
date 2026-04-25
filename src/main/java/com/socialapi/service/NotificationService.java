package com.socialapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final RedisTemplate<String, String> redisTemplate;

    // ------------------------------------------------
    // Called when a bot interacts with a user's post.
    //
    // If user has NOT received a notification in last
    // 15 minutes → send immediately + set cooldown.
    //
    // If user HAS received one recently → push message
    // into a Redis list (pending notifications).
    // ------------------------------------------------
    public void notify(Long userId, String botName, Long postId) {

        String cooldownKey = "notif_cooldown:user_" + userId;
        String pendingKey  = "user:" + userId + ":pending_notifs";
        String message     = "Bot '" + botName + "' replied to your post #" + postId;

        // setIfAbsent = SET NX (atomic check + set)
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", Duration.ofMinutes(15));

        if (Boolean.TRUE.equals(firstTime)) {
            // No cooldown was active → send immediately
            log.info("[NOTIFICATION] Push Notification Sent to User {}: {}", userId, message);
        } else {
            // Cooldown active → buffer it in a Redis list
            redisTemplate.opsForList().rightPush(pendingKey, message);
            log.info("[NOTIFICATION] Buffered for User {}: {}", userId, message);
        }
    }
}
