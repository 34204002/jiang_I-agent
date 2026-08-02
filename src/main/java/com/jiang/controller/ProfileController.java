package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.constant.FileConstants;
import com.jiang.entity.User;
import com.jiang.mapper.UserMapper;
import com.jiang.service.OssService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 个人设置接口
 */
@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private static final long MAX_SIZE = FileConstants.AVATAR_MAX_SIZE; // 2MB
    private final OssService ossService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 上传头像到阿里云 OSS，返回公网 URL
     */
    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return Result.fail(400, "请选择文件");
        }
        if (file.getSize() > MAX_SIZE) {
            return Result.fail(400, "头像不能超过 2MB");
        }

        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals(MediaType.IMAGE_JPEG_VALUE) &&
                        !contentType.equals(MediaType.IMAGE_PNG_VALUE) &&
                        !contentType.equals("image/webp") &&
                        !contentType.equals("image/gif"))) {
            return Result.fail(400, "仅支持 JPG/PNG/WebP/GIF");
        }

        String url = ossService.uploadAvatar(file);
        return Result.success(Map.of("url", url));
    }

    /**
     * 修改密码（校验原密码）
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody Map<String, String> body,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (oldPassword == null || oldPassword.isBlank()) {
            return Result.fail(400, "请输入原密码");
        }
        if (newPassword == null || newPassword.length() < 6) {
            return Result.fail(400, "新密码至少 6 位");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return Result.fail(400, "原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("密码已修改: userId={}", userId);
        return Result.success();
    }
}
