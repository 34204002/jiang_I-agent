package com.jiang.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * <p>
 * 定义异步任务所需的队列、交换机、绑定关系。
 * 主要用于：文档异步解析、向量化削峰、离线 RAG 索引更新。
 * </p>
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    // ==================== 文档解析队列 ====================
    public static final String DOCUMENT_PARSE_QUEUE = "document.parse.queue";
    public static final String DOCUMENT_PARSE_EXCHANGE = "document.parse.exchange";
    public static final String DOCUMENT_PARSE_ROUTING_KEY = "document.parse";

    // ==================== 文档向量化队列 ====================
    public static final String DOCUMENT_EMBED_QUEUE = "document.embed.queue";
    public static final String DOCUMENT_EMBED_EXCHANGE = "document.embed.exchange";
    public static final String DOCUMENT_EMBED_ROUTING_KEY = "document.embed";

    // ==================== 死信队列（处理失败重试） ====================
    public static final String DEAD_LETTER_QUEUE = "dead.letter.queue";
    public static final String DEAD_LETTER_EXCHANGE = "dead.letter.exchange";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead.letter";

    // ==================== 队列定义 ====================

    @Bean
    public Queue documentParseQueue() {
        return QueueBuilder.durable(DOCUMENT_PARSE_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue documentEmbedQueue() {
        return QueueBuilder.durable(DOCUMENT_EMBED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    // ==================== 交换机定义 ====================

    @Bean
    public DirectExchange documentParseExchange() {
        return new DirectExchange(DOCUMENT_PARSE_EXCHANGE);
    }

    @Bean
    public DirectExchange documentEmbedExchange() {
        return new DirectExchange(DOCUMENT_EMBED_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    // ==================== 绑定关系 ====================

    @Bean
    public Binding documentParseBinding() {
        return BindingBuilder.bind(documentParseQueue())
                .to(documentParseExchange())
                .with(DOCUMENT_PARSE_ROUTING_KEY);
    }

    @Bean
    public Binding documentEmbedBinding() {
        return BindingBuilder.bind(documentEmbedQueue())
                .to(documentEmbedExchange())
                .with(DOCUMENT_EMBED_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    // ==================== Listener 工厂 ====================

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // JSON 消息转换，避免手动序列化
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        // 手动 ACK，处理完业务后再确认
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
