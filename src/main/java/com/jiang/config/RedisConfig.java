package com.jiang.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiang.service.RedisChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 基础配置。
 * <p>
 * ChatMemory 使用手动 RedisChatMemory（基于 List，Jackson 序列化，不依赖 Redis Stack）。
 * </p>
 */
@Configuration
public class RedisConfig {

    /**
     * 带 DefaultTyping 的 ObjectMapper，满足 RedisChatMemory 手动序列化需求。
     * <b>注意：此 Bean 会污染全局 Jackson 序列化，API 请求场景应用独立的 ObjectMapper。</b>
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);
        return mapper;
    }

    /**
     * RedisTemplate：Key 用 String 序列化，Value 用默认（JDK 序列化），HashKey 用 String
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public ChatMemory chatMemory(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        return new RedisChatMemory(redisTemplate, objectMapper);
    }
}
