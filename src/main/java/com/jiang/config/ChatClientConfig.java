package com.jiang.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;

/**
 * Spring AI ChatClient 配置 — 预置系统提示词等不变属性，Service 层无需重复指定
 */
@Slf4j
@Configuration
public class ChatClientConfig {

    @Value("classpath:prompts/system.md")
    private Resource systemPromptResource;

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        String defaultSystem;
        try {
            defaultSystem = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("默认系统提示词加载成功，长度: {} 字符", defaultSystem.length());
        } catch (Exception e) {
            log.warn("默认系统提示词加载失败，使用内置提示词", e);
            defaultSystem = "你是 Jiang I-Agent，一个基于 Spring AI 构建的个人 AI 知识库助手。";
        }
        return ChatClient.builder(chatModel)
                .defaultSystem(defaultSystem)
                .build();
    }
}
