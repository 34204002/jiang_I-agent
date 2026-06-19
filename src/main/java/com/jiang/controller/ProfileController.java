package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.service.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
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

    private final OssService ossService;

    private static final long MAX_SIZE = 2 * 1024 * 1024; // 2MB

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
}
