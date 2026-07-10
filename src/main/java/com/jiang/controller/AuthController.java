package com.jiang.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiang.common.Result;
import com.jiang.entity.User;
import com.jiang.mapper.UserMapper;
import com.jiang.model.req.LoginRequest;
import com.jiang.model.req.RegisterRequest;
import com.jiang.model.vo.UserVO;
import com.jiang.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口 — 登录/注册
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 注册（仅 USER 角色）
     */
    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return Result.fail(400, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return Result.fail(400, "密码至少 6 位");
        }

        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (exist != null) {
            return Result.fail(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setRole("USER");
        userMapper.insert(user);

        log.info("新用户注册: {}", user.getUsername());
        return Result.success(UserVO.from(user));
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return Result.fail(401, "用户名或密码错误");
        }

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        log.info("用户登录: {}", user.getUsername());
        return Result.success(Map.of(
                "token", token,
                "user", UserVO.from(user)
        ));
    }

}
