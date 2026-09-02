package com.jiang.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiang.config.RabbitConfig;
import com.jiang.entity.Document;
import com.jiang.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 死信队列监听 — 处理重试耗尽的消息（毒消息），把文档标记为 status=3(PROCESSING_FAILED)，
 * 封死「永远卡在处理中」的僵尸文档，并保留失败现场到日志。
 * <p>
 * 接收 {@link Message} 原样 body 手动解析，避免二次反序列化失败再次触发死信（DLQ 不再设 DLX）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqConsumer {

    private static final String FAIL_REASON = "处理失败（已重试 3 次），请重新上传";

    private final DocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.DLQ)
    public void onDlq(Message raw) {
        String body = new String(raw.getBody(), StandardCharsets.UTF_8);
        Long docId = extractDocId(body);
        if (docId != null) {
            Document doc = documentMapper.selectById(docId);
            if (doc != null && doc.getStatus() != null && doc.getStatus() != 2) {
                doc.setStatus(3);
                doc.setErrorMessage(FAIL_REASON);
                documentMapper.updateById(doc);
                log.error("[KNOWLEDGE_DLQ] 文档已标记失败: id={}, filename={}", docId, doc.getFilename());
            }
        } else {
            log.error("[KNOWLEDGE_DLQ] 无法从消息解析 docId，原始消息: {}", body);
        }
    }

    private Long extractDocId(String body) {
        try {
            long v = objectMapper.readTree(body).path("docId").asLong(-1);
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }
}