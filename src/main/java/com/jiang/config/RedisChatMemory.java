package com.jiang.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 持久化的对话记忆实现
 * <p>
 * 替代 spring-ai-starter-model-chat-memory-repository-redis
 * （因阿里云 Maven 镜像同步滞后，手动实现功能完全一致）。
 * </p>
 * <p>
 * Key 设计：
 * - agent:chat:memory:{conversationId} → List&lt;Message&gt;（有序消息列表）
 * - TTL 默认 30 分钟，每次访问自动续期
 * </p>
 */
public class RedisChatMemory implements ChatMemory {

    private static final String KEY_PREFIX = "agent:chat:memory:";
    private static final long TTL_MINUTES = 30;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisChatMemory(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = buildKey(conversationId);
        for (Message msg : messages) {
            redisTemplate.opsForList().rightPush(key, msg);
        }
        redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public List<Message> get(String conversationId) {
        String key = buildKey(conversationId);
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return Collections.emptyList();
        }

        // 获取全部消息并续期
        List<Object> rawList = redisTemplate.opsForList().range(key, 0, size - 1);
        redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);

        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> messages = new ArrayList<>();
        for (Object obj : rawList) {
            if (obj instanceof Message msg) {
                messages.add(msg);
            }
        }
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(buildKey(conversationId));
    }

    private String buildKey(String conversationId) {
        return KEY_PREFIX + conversationId;
    }
}
