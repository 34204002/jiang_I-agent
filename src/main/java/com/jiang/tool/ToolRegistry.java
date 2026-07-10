/*
 * Copyright (c) 2026 Jiang. 自研 @Tool 注解框架核心。
 */
package com.jiang.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心 — 在应用就绪后扫描所有 @Tool 方法，运行时按 LLM 请求派发执行。
 * <p>
 * 延迟扫描（{@link ApplicationReadyEvent}）避免在 Bean 创建阶段触发循环依赖。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, ToolDef> tools = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ApplicationContext ctx;

    @EventListener(ApplicationReadyEvent.class)
    public void scanTools() {
        String[] beanNames = ctx.getBeanDefinitionNames();
        for (String name : beanNames) {
            Object bean;
            try {
                bean = ctx.getBean(name);
            } catch (Exception e) {
                continue;
            }
            Class<?> clazz = bean.getClass();
            if (clazz.getName().contains("$$")) {
                clazz = clazz.getSuperclass();
            }
            for (Method method : clazz.getDeclaredMethods()) {
                Tool anno = method.getAnnotation(Tool.class);
                if (anno == null) continue;
                ToolDef def = new ToolDef(anno.name(), anno.description(),
                        anno.parameters(), bean, method);
                tools.put(anno.name(), def);
                log.info("注册工具: {} — {}", anno.name(), anno.description());
            }
        }
        log.info("工具注册完成，共 {} 个工具", tools.size());
    }

    // ==================== 扫描注册（延迟到应用就绪后） ====================

    public List<Map<String, Object>> getToolsJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ToolDef def : tools.values()) {
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", def.name());
            func.put("description", def.description());
            try {
                JsonNode params = objectMapper.readTree(def.paramsJson());
                // DeepSeek 要求 schema 必须含 type:object
                if (!params.has("type")) {
                    params = objectMapper.readTree(
                            "{\"type\":\"object\",\"properties\":" + params + "}");
                }
                func.put("parameters", params);
            } catch (JsonProcessingException e) {
                log.warn("工具 {} 的 params JSON 解析失败，跳过", def.name(), e);
                continue;
            }
            list.add(Map.of("type", "function", "function", func));
        }
        return list;
    }

    // ==================== LLM Schema 构建 ====================

    public int count() {
        return tools.size();
    }

    public boolean hasTools() {
        return !tools.isEmpty();
    }

    public String execute(String name, String arguments) {
        ToolDef def = tools.get(name);
        if (def == null) return "错误: 未知工具 " + name;

        Method method = def.method();
        Parameter[] params = method.getParameters();

        try {
            if (params.length == 0) {
                Object result = method.invoke(def.bean());
                return result != null ? result.toString() : "完成";
            }

            JsonNode argsNode = objectMapper.readTree(arguments);
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                String paramName = params[i].getName();
                Class<?> type = params[i].getType();
                JsonNode val = argsNode.get(paramName);

                if (val == null || val.isNull()) {
                    args[i] = null;
                } else if (type == String.class) {
                    // 字符串参数：如果是数组或对象节点，序列化为 JSON 字符串（如 concepts 数组）
                    args[i] = val.isArray() || val.isObject() ? val.toString() : val.asText();
                } else if (type == Integer.class || type == int.class) {
                    args[i] = val.asInt();
                } else if (type == Long.class || type == long.class) {
                    args[i] = val.asLong();
                } else if (type == Boolean.class || type == boolean.class) {
                    args[i] = val.asBoolean();
                } else {
                    args[i] = val.asText();
                }
            }

            Object result = method.invoke(def.bean(), args);
            return result != null ? result.toString() : "完成";

        } catch (InvocationTargetException e) {
            log.error("工具 {} 执行异常", name, e.getCause());
            return "错误: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            log.error("工具 {} 调用失败", name, e);
            return "错误: " + e.getMessage();
        }
    }

    // ==================== 执行调度 ====================

    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ToolDef def : tools.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", def.name());
            m.put("description", def.description());
            list.add(m);
        }
        return list;
    }

    /**
     * 每个工具定义
     */
    record ToolDef(String name, String description, String paramsJson, Object bean, Method method) {
    }
}
