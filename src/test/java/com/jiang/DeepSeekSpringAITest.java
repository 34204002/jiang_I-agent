package com.jiang;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class DeepSeekSpringAITest {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekSpringAITest.class);

    @Autowired
    private ChatModel chatModel;

    @Test
    void testReasonerStreamWithReasoning() throws Exception {
        log.info("=== deepseek-reasoner 流式 + reasoningContent ===");

        var options = OpenAiChatOptions.builder()
                .model("deepseek-reasoner")
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder reasoning = new StringBuilder();
        int[] rcChunks = {0}, total = {0};

        chatModel.stream(new Prompt("1+1等于几？一句话。", options))
                .doOnNext(chunk -> {
                    total[0]++;
                    Object rc = chunk.getResult().getOutput().getMetadata().get("reasoningContent");
                    if (rc != null && !rc.toString().isEmpty()) {
                        rcChunks[0]++;
                        reasoning.append(rc);
                    }
                })
                .doOnComplete(() -> {
                    log.info("总chunk: {} | rcChunk: {} | rc长度: {}",
                            total[0], rcChunks[0], reasoning.length());
                    log.info("结论: {}", reasoning.length() > 0 ? "🟢 SpringAI可用" : "🔴 不可用");
                    latch.countDown();
                })
                .doOnError(e -> {
                    log.error("失败: {}", e.getMessage());
                    latch.countDown();
                })
                .subscribe();

        latch.await(90, TimeUnit.SECONDS);
        if (reasoning.length() == 0) throw new AssertionError("reasoningContent 为空");
    }

    @Test
    void testV4FlashNoReasoning() throws Exception {
        log.info("=== v4-flash + thinking extraBody（不支持推理）===");

        var options = OpenAiChatOptions.builder()
                .model("deepseek-v4-flash")
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder reasoning = new StringBuilder();

        chatModel.stream(new Prompt("1+1等于几？一句话。", options))
                .doOnNext(chunk -> {
                    Object rc = chunk.getResult().getOutput().getMetadata().get("reasoningContent");
                    if (rc != null && !rc.toString().isEmpty()) reasoning.append(rc);
                })
                .doOnComplete(() -> {
                    log.info("v4-flash reasoningContent: {} chars -> {}",
                            reasoning.length(), reasoning.length() > 0 ? "有" : "无(预期)");
                    latch.countDown();
                })
                .doOnError(e -> {
                    log.error("失败: {}", e.getMessage());
                    latch.countDown();
                })
                .subscribe();

        latch.await(60, TimeUnit.SECONDS);
    }
}
