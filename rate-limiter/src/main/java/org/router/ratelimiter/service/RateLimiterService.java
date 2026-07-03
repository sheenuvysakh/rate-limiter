package org.router.ratelimiter.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = RedisScript.of(getLuaScript(), Long.class);
    }

    public enum RequestResult {
        ALLOWED, DELAYED, BLOCKED
    }

    public RequestResult evaluateRequest(String fingerprint) {
        String banKey = "rate:ban:" + fingerprint;
        String trackingKey = "rate:track:" + fingerprint;

        Long result = redisTemplate.execute(
                rateLimitScript,
                List.of(banKey, trackingKey),
                "5", "2", "600" // Max burst, Window, Ban duration
        );

        if (result == null || result == 0) {
            //completely cuts off client when they exceeds threshold of 10
            return RequestResult.BLOCKED;
        }

        if (result == 2) {
            // Tarpit Action - force a 5-second penalty delay on this request thread
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return RequestResult.DELAYED;
        }

        return RequestResult.ALLOWED;
    }


    /**
     * Inline Lua Script executed atomically inside Redis.
     */
    private static String getLuaScript() {
        return """
                -- 1. Check if user is already hard banned
                if redis.call('EXISTS', KEYS[1]) == 1 then
                    return 0 -- Action: Blocked
                end
                
                -- 2. Increment the velocity counter
                local current_burst = redis.call('INCR', KEYS[2])
                
                -- 3. Set short expiration window on first hit
                if current_burst == 1 then
                    redis.call('EXPIRE', KEYS[2], ARGV[2])
                end
                
                -- 4. Evaluate Threshold Layers
                if current_burst > 10 then
                    -- Aggressive spike detected: Trigger Hard Ban
                    redis.call('SET', KEYS[1], 'BANNED')
                    redis.call('EXPIRE', KEYS[1], ARGV[3])
                    redis.call('DEL', KEYS[2])
                    return 0 -- Action: Blocked
                elseif current_burst > 5 then
                    return 2 -- Action: Warn & Delay (Tarpit)
                end
                
                return 1 -- Action: Allowed Cleanly
            """;
    }
}