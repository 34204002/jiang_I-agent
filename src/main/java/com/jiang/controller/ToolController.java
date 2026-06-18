package com.jiang.controller;

import com.jiang.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工具接口 — Phase 4
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    /** 获取 Agent 当前注册的所有工具 */
    @GetMapping
    public Result<Map<String, List<Map<String, String>>>> listTools() {
        // TODO: Phase 4 — 从 ToolRegistry 读取工具元数据
        return Result.fail("not implemented");
    }
}
