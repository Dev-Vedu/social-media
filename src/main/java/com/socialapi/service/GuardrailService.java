package com.socialapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuardrailService {

    private final RedisTemplate<String, String> redisTemplate;

    // ------------------------------------------------
    // Virality Score
    // key:  post:{postId}:virality_score
    // ------------------------------------------------
    public void incrementVirality(Long postId, long points) {
        String key = "post:" + postId + ":virality_score";
        redisTemplate.opsForValue().increment(key, points);
        log.info("Virality updated: post {} +{} points", postId, points);
    }

    // ------------------------------------------------
    // Horizontal Cap — max 100 bot replies per post
    // key:  post:{postId}:bot_count
    //
    // We use a Lua script so the check + increment
    // happen ATOMICALLY (no race condition possible).
    // If two bots hit at the same time, only one wins.
    // ------------------------------------------------
    public boolean tryIncrementBotCount(Long postId) {
        String key = "post:" + postId + ":bot_count";

        // Lua script runs as one atomic operation in Redis
        String lua =
            "local count = tonumber(redis.call('GET', KEYS[1])) or 0 " +
            "if count >= 100 then " +
            "  return 0 " +       // cap reached, reject
            "else " +
            "  redis.call('INCR', KEYS[1]) " +
            "  return 1 " +       // allowed
            "end";

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(lua, Long.class);
        Long result = redisTemplate.execute(script, List.of(key));

        return Long.valueOf(1L).equals(result);
    }

    // ------------------------------------------------
    // Cooldown Cap — bot cannot reply to same human
    // more than once in 10 minutes
    // key:  cooldown:bot_{botId}:human_{humanId}
    //
    // setIfAbsent = SET NX in Redis (atomic)
    // returns true  → no cooldown, interaction allowed
    // returns false → cooldown active, block the bot
    // ------------------------------------------------
    public boolean checkAndSetCooldown(Long botId, Long humanId) {
        String key = "cooldown:bot_" + botId + ":human_" + humanId;
        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofMinutes(10));
        return Boolean.TRUE.equals(set);
    }
}
