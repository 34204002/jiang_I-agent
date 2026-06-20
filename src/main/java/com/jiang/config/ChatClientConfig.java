package com.jiang.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;

/**
 * 系统提示词配置——从 {@code prompts/system.md} 加载默认提示词。
 */
@Slf4j
@Configuration
public class ChatClientConfig {

    @Value("classpath:prompts/system.md")
    private Resource systemPromptResource;

    @Bean("defaultSystemPrompt")
    public String defaultSystemPrompt() {
        try {
            String prompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("默认系统提示词加载成功，长度: {} 字符", prompt.length());
            return prompt;
        } catch (Exception e) {
            log.warn("默认系统提示词加载失败，使用内置提示词", e);
            return "你是 Jiang I-Agent，一个基于 Spring AI 构建的个人 AI 知识库助手。";
        }
    }
}
