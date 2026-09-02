package com.jiang.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiang.config.RabbitConfig;
import com.jiang.model.mq.UploadMessage;
import com.jiang.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 文档异步处理消费者 — 收到消息后从 OSS 取回字节，完成解析/分块/向量化。
 * <p>
 * 消息体为显式 JSON 字节，这里原样收 {@link Message} 手动解析，不依赖版本化的转换器。
 * 重试（max-attempts=3 指数退避）与并发（2~4）由 yml 的 spring.rabbitmq.listener.simple.* 控制；
 * 瞬态失败自动重试，耗尽后 AMQP 拒绝 → 进死信队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorizeConsumer {

    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.PROCESS_QUEUE)
    public void process(Message raw) throws IOException {
        String body = new String(raw.getBody(), StandardCharsets.UTF_8);
        UploadMessage msg;
        try {
            msg = objectMapper.readValue(body, UploadMessage.class);
        } catch (Exception e) {
            // 无法解析的消息没有重试价值，直接拒绝进 DLQ
            log.warn("[KNOWLEDGE_MQ] 消息无法解析，拒绝: {}", body);
            throw new AmqpRejectAndDontRequeueException("消息无法解析", e);
        }
        if (msg.docId() == null) {
            throw new AmqpRejectAndDontRequeueException("消息缺少 docId");
        }
        log.info("[KNOWLEDGE_MQ] 开始处理: {}", msg);
        knowledgeService.processDocument(msg.docId());
    }
}