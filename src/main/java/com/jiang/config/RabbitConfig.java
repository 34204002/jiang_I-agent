package com.jiang.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑 — 知识库文档异步处理。
 * <p>
 * 队列只传凭证（{@code docId/userId/hash}），文件字节始终留在 OSS，消费者按需取回。
 * 消息体为显式 JSON 字节（两侧用 ObjectMapper 收发），不依赖版本化的类型头/转换器配置。
 * 重试/并发/预取参数放 {@code application-dev.yml}（spring.rabbitmq.listener.simple.*）。
 *
 * <pre>
 * producer ──(knowledge.upload / 'process')──▶ knowledge.upload.process
 *                                                  │ x-dead-letter-exchange
 *                                                  ▼
 *                                              knowledge.upload.dlx ─▶ knowledge.upload.dlq
 * </pre>
 */
@Configuration
public class RabbitConfig {

    /** 主交换（direct, durable） */
    public static final String UPLOAD_EXCHANGE = "knowledge.upload";
    /** 主任务路由键 */
    public static final String PROCESS_ROUTING_KEY = "process";
    /** 主任务队列 */
    public static final String PROCESS_QUEUE = "knowledge.upload.process";
    /** 死信交换 */
    public static final String DLX = "knowledge.upload.dlx";
    /** 死信路由键 */
    public static final String DLQ_ROUTING_KEY = "dlq";
    /** 死信队列 */
    public static final String DLQ = "knowledge.upload.dlq";

    @Bean
    public DirectExchange uploadExchange() {
        return new DirectExchange(UPLOAD_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue processQueue() {
        return QueueBuilder.durable(PROCESS_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue uploadDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding processBinding() {
        return BindingBuilder.bind(processQueue()).to(uploadExchange()).with(PROCESS_ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(uploadDlq()).to(deadLetterExchange()).with(DLQ_ROUTING_KEY);
    }
}