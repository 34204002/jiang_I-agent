package com.jiang.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis List + Jackson 的 ChatMemory 实现。
 * <p>
 * 替代官方 spring-ai-starter-model-chat-memory-repository-redis，
 * 避免 Redis Stack 的 FT._LIST 等 RediSearch 命令依赖。
 * Message 通过 Jackson 序列化为 JSON 存入 Redis List。
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

	@Override
	public void add(String conversationId, List<Message> messages) {
		String key = buildKey(conversationId);
		for (Message msg : messages) {
			try {
				String json = objectMapper.writeValueAsString(msg);
				redisTemplate.opsForList().rightPush(key, json);
			} catch (JsonProcessingException e) {
				log.error("ChatMemory 序列化失败: conversationId={}", conversationId, e);
			}
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

		List<Object> rawList = redisTemplate.opsForList().range(key, 0, size - 1);
		redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);

		if (rawList == null || rawList.isEmpty()) {
			return Collections.emptyList();
		}

		List<Message> messages = new ArrayList<>();
		for (Object obj : rawList) {
			try {
				Message msg = objectMapper.readValue(obj.toString(), Message.class);
				messages.add(msg);
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

	private String buildKey(String conversationId) {
		return KEY_PREFIX + conversationId;
	}
}
