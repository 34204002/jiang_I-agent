package com.jiang.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis List + 手动 JSON 序列化的 ChatMemory 实现。
 * <p>
 * 不使用 Jackson 多态反序列化 Spring AI 内部 Message 类（避免构造函数匹配问题），
 * 改为手动 Map 序列化：{@code {"type":"USER","content":"..."}}。
 * </p>
 */
@Slf4j
public class RedisChatMemory implements ChatMemory {

    private static final String KEY_PREFIX = "agent:chat:memory:";
    private static final long TTL_MINUTES = 30;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatMemory(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================== ChatMemory 接口 ====================

    /**
     * 追加消息到会话记忆，写入后刷新 TTL（滑动过期）。
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = buildKey(conversationId);
        for (Message msg : messages) {
            try {
                String json = serializeMessage(msg);
                redisTemplate.opsForList().rightPush(key, json);
            } catch (JsonProcessingException e) {
                log.error("ChatMemory 序列化失败: conversationId={}", conversationId, e);
            }
        }
        redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 获取会话全部历史消息，读取时刷新 TTL（滑动过期）。
     */
    @Override
    public List<Message> get(String conversationId) {
        String key = buildKey(conversationId);
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return Collections.emptyList();
        }

        List<Object> rawList = redisTemplate.opsForList().range(key, 0, size - 1);
        // 读取时刷新 TTL，实现滑动过期
        redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);

        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> messages = new ArrayList<>();
        for (Object obj : rawList) {
            try {
                Message msg = deserializeMessage(obj.toString());
                if (msg != null) {
                    messages.add(msg);
                }
            } catch (JsonProcessingException e) {
                log.error("ChatMemory 反序列化失败", e);
            }
        }
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(buildKey(conversationId));
    }

    // ==================== 内部：手动序列化 ====================

    /**
     * 将 Message 序列化为 {@code {"type":"USER","content":"..."}} 格式。
     * 不依赖 Jackson 多态，避免 Spring AI 内部类构造函数不匹配。
     */
    private String serializeMessage(Message msg) throws JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", msg.getMessageType().name());   // USER / ASSISTANT / TOOL / SYSTEM
        map.put("content", msg.getText());
        return objectMapper.writeValueAsString(map);
    }

    /**
     * 将 JSON 反序列化为对应 Message 子类。
     */
    private Message deserializeMessage(String json) throws JsonProcessingException {
        Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        String type = (String) map.get("type");
        String content = (String) map.get("content");
        if (type == null || content == null) {
            log.warn("ChatMemory 反序列化数据不完整: {}", json);
            return null;
        }
        return switch (type) {
            case "USER" -> new UserMessage(content);
            case "ASSISTANT" -> new AssistantMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            // TOOL 类型暂不处理，Phase 4 完善
            default -> {
                log.warn("ChatMemory 未知消息类型: {}", type);
                yield null;
            }
        };
    }

    // ==================== 内部：Redis key ====================

    private String buildKey(String conversationId) {
        return KEY_PREFIX + conversationId;
    }
}
