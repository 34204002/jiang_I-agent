package com.jiang.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 配置
 * <p>
 * ChatClient 是 Spring AI 提供的统一对话入口，封装了：
 * - 对话记忆管理（通过 ChatMemory Advisor）
 * - Function Calling 工具调用
 * - Prompt 模板渲染
 * - 流式/同步响应切换
 * </p>
 */
@Configuration
public class ChatClientConfig {

    /**
     * 构建全局 ChatClient Bean。
     * ChatModel 由 Spring AI DeepSeek Starter 自动配置。
     * ChatMemory 由 RedisConfig 手动创建，在调用时通过 advisor 注入。
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
