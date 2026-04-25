package com.socialapi.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final RedisTemplate<String, String> redisTemplate;

    //5min
    @Scheduled(fixedRate = 300000)
    public void sweepPendingNotifications() {

        log.info("[CRON] Running notification sweep...");

        Set<String> keys = redisTemplate.keys("user:*:pending_notifs");

        if (keys == null || keys.isEmpty()) {
            log.info("[CRON] No pending notifications found.");
            return;
        }

        for (String key : keys) {

            //pending msg
            List<String> messages = redisTemplate.opsForList().range(key, 0, -1);

            if (messages == null || messages.isEmpty()) continue;


            redisTemplate.delete(key);

            String userId = key.split(":")[1];

            int total = messages.size();
            String firstMsg = messages.get(0);

            if (total == 1) {
                log.info("[NOTIFICATION] Summarised Push Notification to User {}: {}", userId, firstMsg);
            } else {
                //extract bot name
                String botName = "Unknown";
                try {
                    int start = firstMsg.indexOf('\'') + 1;
                    int end   = firstMsg.indexOf('\'', start);
                    botName   = firstMsg.substring(start, end);
                } catch (Exception ignored) {}

                log.info("[NOTIFICATION] Summarised Push Notification to User {}: Bot '{}' and {} others interacted with your posts.",
                        userId, botName, total - 1);
            }
        }
    }
}
