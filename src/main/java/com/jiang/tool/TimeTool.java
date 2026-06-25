package com.jiang.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具 — 解决 LLM 不知道"现在几点"的问题。
 */
@Slf4j
@Component
public class TimeTool {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)");

    @Tool(name = "get_current_time",
          description = "获取当前日期和时间。当你需要知道「现在几点」「今天是几号」「当前时间」时使用。"
                  + "LLM 自身无法感知时间，必须调用此工具获取。",
          parameters = "{}")
    public String getCurrentTime() {
        String now = LocalDateTime.now(ZONE).format(FMT);
        log.info("当前时间: {}", now);
        return now + " (Asia/Shanghai)";
    }
}
