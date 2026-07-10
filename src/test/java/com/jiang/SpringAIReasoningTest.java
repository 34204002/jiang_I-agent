package com.jiang;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 验证 Spring AI 2.0 OpenAiChatModel 是否保留 reasoningContent 到 metadata。
 * <p>
 * 完全使用项目配置（dev profile），Web 环境随机端口避免冲突。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class SpringAIReasoningTest {

    private static final Logger log = LoggerFactory.getLogger(SpringAIReasoningTest.class);

    @Autowired
    private ChatModel chatModel;

    @Test
    void testStreamR1ReasoningContent() throws Exception {
        log.info("ChatModel 类型: {}", chatModel.getClass().getName());

        // R1 原生推理，始终返回 reasoning_content
        var options = OpenAiChatOptions.builder()
                .model("deepseek-ai/DeepSeek-R1")
                .build();

        Prompt prompt = new Prompt("1 + 1 = ? 请用一句话回答。", options);

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder reasoning = new StringBuilder();
        int[] total = {0}, rcChunks = {0};
        boolean[] printed = {false};

        chatModel.stream(prompt)
                .doOnNext(chunk -> {
                    total[0]++;
                    AssistantMessage msg = chunk.getResult().getOutput();
                    var meta = msg.getMetadata();

                    // 打印前 5 个 chunk 的完整 metadata（排查 key 名差异）
                    if (total[0] <= 5) {
                        log.info("Chunk #{} | content=[{}] | metadata keys={}",
                                total[0],
                                msg.getText() != null ? msg.getText().substring(0,
                                        Math.min(60, msg.getText().length())) : "null",
                                meta.keySet());
                        // 打印所有 metadata 键值对
                        for (var entry : meta.entrySet()) {
                            String val = entry.getValue() != null
                                    ? entry.getValue().toString()
                                    : "null";
                            log.info("  metadata[\"{}\"] = \"{}\"",
                                    entry.getKey(),
                                    val.substring(0, Math.min(120, val.length())));
                        }
                    }

                    // 尝试多种可能的 key 名
                    Object rc = meta.get("reasoningContent");
                    if (rc == null) rc = meta.get("reasoning_content");
                    if (rc == null) rc = meta.get("reasoning");
                    if (rc == null) rc = meta.get("thinking");

                    if (rc != null && !rc.toString().isEmpty()) {
                        rcChunks[0]++;
                        reasoning.append(rc);
                    }
                })
                .doOnComplete(() -> {
                    log.info("总 chunk: {} | 含 rc: {} | rc 长度: {}",
                            total[0], rcChunks[0], reasoning.length());

                    if (reasoning.length() > 0) {
                        log.info("🟢 Spring AI 2.0 ChatModel.stream() 保留了 reasoningContent!");
                        log.info("--- rc 前 400 字符 ---");
                        log.info(reasoning.substring(0, Math.min(400, reasoning.length())));
                    } else {
                        log.error("🔴 未在 metadata 中找到 reasoningContent");
                    }
                    latch.countDown();
                })
                .doOnError(e -> {
                    log.error("流式失败: {}", e.getMessage());
                    latch.countDown();
                })
                .subscribe();

        latch.await(120, TimeUnit.SECONDS);

        if (reasoning.length() == 0) {
            throw new AssertionError("🔴 reasoningContent 未在 ChatModel.stream() metadata 中找到。");
        }
    }
}
