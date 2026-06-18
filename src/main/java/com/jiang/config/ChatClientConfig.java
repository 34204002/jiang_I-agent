package com.jiang.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 配置。
 * <p>
 * ChatClient 是 Spring AI 的统一对话入口。
 * ChatMemory 上下文管理在 ChatService 中手动处理（避免 streaming 时 advisor 丢失 conversationId 的 Bug）。
 * </p>
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
