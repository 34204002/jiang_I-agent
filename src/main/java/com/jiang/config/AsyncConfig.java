package com.jiang.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 * <p>
 * 使用场景：
 * - 文档异步解析（Tika 解析大文件时不阻塞主线程）
 * - 向量化批量任务
 * - 非关键路径的日志/统计写入
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Agent 业务专用线程池
     * <p>
     * Core=4, Max=8：满足中小规模并发，避免线程膨胀。
     * CallerRunsPolicy：队列满时由调用线程执行，产生自然背压。
     * </p>
     */
    @Bean("agentTaskExecutor")
    public ThreadPoolTaskExecutor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("agent-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
