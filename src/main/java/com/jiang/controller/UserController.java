package com.jiang.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jiang.common.Result;
import com.jiang.entity.User;
import com.jiang.mapper.UserMapper;
import com.jiang.model.vo.UserVO;
import com.jiang.util.CryptoUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
     * 用户自填 API Key 的落库加密密钥（与 ChatService 解密用同一个值）
     */
    @Value("${app.llm-key-secret}")
    private String llmKeySecret;

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
        return Result.success(withKeyMasked(UserVO.from(user), user));
    }

    /**
     * 修改个人信息（昵称、头像、BYOK：模型名 / API Key）
     * <p>
     * apiKey：非空时加密入库；显式传空串 = 清空回退全局。llmModel：非空时更新；缺省不改。
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
        if (body.containsKey("llmModel")) {
            user.setLlmModel(body.get("llmModel"));
        }
        // apiKey 显式传空串 = 清除（回退全局 key）；MyBatis-Plus updateById 忽略 null，
        // 因此清空必须用 UpdateWrapper 显式 set null，否则库里旧密文残留、清除失效。
        boolean clearKey = body.containsKey("apiKey")
                && (body.get("apiKey") == null || body.get("apiKey").isBlank());
        if (body.containsKey("apiKey") && !clearKey) {
            user.setApiKeyEnc(CryptoUtil.encrypt(body.get("apiKey").trim(), llmKeySecret));
        }
        userMapper.updateById(user);
        if (clearKey) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getApiKeyEnc, null));
            user.setApiKeyEnc(null);
        }

        return Result.success(withKeyMasked(UserVO.from(user), user));
    }

    /**
     * 从实体解密并填充脱敏回显。字段未配置时为 null，前端据此显示"未设置"。
     */
    private UserVO withKeyMasked(UserVO vo, User user) {
        if (user.getApiKeyEnc() != null && !user.getApiKeyEnc().isEmpty()) {
            String plain = CryptoUtil.decrypt(user.getApiKeyEnc(), llmKeySecret);
            vo.setApiKeyMasked(CryptoUtil.mask(plain));
        }
        return vo;
    }

}