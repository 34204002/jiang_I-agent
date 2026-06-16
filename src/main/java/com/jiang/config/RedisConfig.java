package com.jiang.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 * <p>
 * 职责：
 * - RedisTemplate：会话缓存、向量元数据、限流计数器
 * - RedissonClient：由 redisson-spring-boot-starter 自动配置，用于分布式锁、令牌桶限流
 * - ChatMemory：持久化对话记忆，基于 Redis List 实现
 * </p>
 */
@Configuration
public class RedisConfig {

    /**
     * 通用 RedisTemplate，Key 用 String 序列化，Value 用 JSON 序列化。
     * 确保存入 Redis 的数据可读、可调试。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 序列化 — JSON 格式，支持复杂对象
        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis 持久化对话记忆 Bean。
     * 支持按 conversationId 隔离的多会话管理，自动过期清理。
     */
    @Bean
    public ChatMemory chatMemory(RedisTemplate<String, Object> redisTemplate) {
        return new RedisChatMemory(redisTemplate);
    }
}
