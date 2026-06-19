package com.jiang.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiang.common.Result;
import com.jiang.entity.AgentConfig;
import com.jiang.entity.User;
import com.jiang.mapper.AgentConfigMapper;
import com.jiang.mapper.UserMapper;
import com.jiang.model.PageResult;
import com.jiang.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员接口 — 仅 ADMIN 角色可访问
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserMapper userMapper;
    private final AgentConfigMapper agentConfigMapper;

    /** 校验管理员 */
    private boolean isAdmin(HttpServletRequest request) {
        return "ADMIN".equals(request.getAttribute("role"));
    }

    // ==================== 用户管理 ====================

    /** 用户列表 */
    @GetMapping("/users")
    public Result<PageResult<UserVO>> listUsers(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 HttpServletRequest request) {
        if (!isAdmin(request)) return Result.fail(403, "仅管理员可操作");

        long total = userMapper.selectCount(null);
        List<UserVO> vos = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .orderByDesc(User::getCreatedAt)
                        .last("LIMIT " + ((page - 1) * size) + "," + size)
        ).stream().map(UserVO::from).toList();

        return Result.success(PageResult.of(total, page, size, vos));
    }

    /** 删除用户 */
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        if (!isAdmin(request)) return Result.fail(403, "仅管理员可操作");
        userMapper.deleteById(id);
        return Result.success();
    }

    // ==================== Agent 公开信息 ====================

    /** Agent 公开信息（名称、头像），前端展示用，无需鉴权 */
    @GetMapping("/agent/profile")
    public Result<java.util.Map<String, String>> getAgentProfile() {
        AgentConfig config = agentConfigMapper.selectById(1);
        return Result.success(java.util.Map.of(
                "agentName", config != null ? config.getAgentName() : "Jiang I-Agent",
                "avatar", config != null && config.getAvatar() != null ? config.getAvatar() : ""
        ));
    }

    // ==================== Agent 配置管理 ====================

    /** 获取 Agent 全局配置 */
    @GetMapping("/agent")
    public Result<AgentConfig> getAgentConfig(HttpServletRequest request) {
        if (!isAdmin(request)) return Result.fail(403, "仅管理员可操作");
        return Result.success(agentConfigMapper.selectById(1));
    }

    /** 更新 Agent 全局配置 */
    @PutMapping("/agent")
    public Result<AgentConfig> updateAgentConfig(@RequestBody AgentConfig config,
                                                   HttpServletRequest request) {
        if (!isAdmin(request)) return Result.fail(403, "仅管理员可操作");
        config.setId(1);
        agentConfigMapper.updateById(config);
        return Result.success(agentConfigMapper.selectById(1));
    }

}
