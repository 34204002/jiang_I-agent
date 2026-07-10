package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.entity.User;
import com.jiang.mapper.UserMapper;
import com.jiang.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 个人信息接口 — 登录用户管理自己的资料
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;

    /**
     * 查看个人信息
     */
    @GetMapping("/me")
    public Result<UserVO> getMe(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        return Result.success(UserVO.from(user));
    }

    /**
     * 修改个人信息（昵称、头像）
     */
    @PutMapping("/me")
    public Result<UserVO> updateMe(@RequestBody Map<String, String> body,
                                   HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }

        if (body.containsKey("nickname")) {
            user.setNickname(body.get("nickname"));
        }
        if (body.containsKey("avatar")) {
            user.setAvatar(body.get("avatar"));
        }
        userMapper.updateById(user);

        return Result.success(UserVO.from(user));
    }

}
