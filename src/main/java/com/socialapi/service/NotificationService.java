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

    public void notify(Long userId, String botName, Long postId) {

        String cooldownKey = "notif_cooldown:user_" + userId;
        String pendingKey  = "user:" + userId + ":pending_notifs";
        String message     = "Bot '" + botName + "' replied to your post #" + postId;

        //selfabsent atomic
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", Duration.ofMinutes(15));

        if (Boolean.TRUE.equals(firstTime)) {
            //no cooldown
            log.info("[NOTIFICATION] Push Notification Sent to User {}: {}", userId, message);
        } else {
            //cooldown activate
            redisTemplate.opsForList().rightPush(pendingKey, message);
            log.info("[NOTIFICATION] Buffered for User {}: {}", userId, message);
        }
    }
}
