package org.router.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;

    // Production-tuned constants
    private static final String MAX_BURST = "5";
    private static final String TRACK_WINDOW_SECS = "2";
    private static final String BAN_WINDOW_SECS = "600"; // 10 minutes

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = RedisScript.of(getLuaScript(), Long.class);
    }

    public boolean isAllowed(String apiKey) {
        String banKey = "rate:ban:" + apiKey;
        String trackingKey = "rate:track:" + apiKey;

        // Execute the script atomically inside Redis
        // Returns: 0 if banned, 1 if allowed, 2 if just caught and banned now
        Long result = redisTemplate.execute(
                rateLimitScript,
                List.of(banKey, trackingKey), // KEYS[1], KEYS[2]
                MAX_BURST, TRACK_WINDOW_SECS, BAN_WINDOW_SECS // ARGV[1], ARGV[2], ARGV[3]
        );

        return result != null && result == 1;
    }

    /**
     * Inline Lua Script executed atomically inside Redis.
     */
    private static String getLuaScript() {
        return """
            -- 1. Check if user is already banned
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end

            -- 2. Increment the rapid-fire counter
            local current_burst = redis.call('INCR', KEYS[2])

            -- 3. If first hit, set short expiration window atomically
            if current_burst == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[2])
            end

            -- 4. Check if they just tripped the brute-force threshold
            if current_burst > tonumber(ARGV[1]) then
                -- Trigger the 10-minute ban
                redis.call('SET', KEYS[1], 'BANNED')
                redis.call('EXPIRE', KEYS[1], ARGV[3])
                -- Clean up tracking data
                redis.call('DEL', KEYS[2])
                return 0
            end

            return 1
            """;
    }
}