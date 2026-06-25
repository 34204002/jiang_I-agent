package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工具接口 — 返回 Agent 已注册的工具列表。
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolRegistry toolRegistry;

    @GetMapping
    public Result<List<java.util.Map<String, Object>>> listTools() {
        return Result.success(toolRegistry.listTools());
    }
}
