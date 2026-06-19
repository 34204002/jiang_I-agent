package com.jiang.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import com.jiang.config.OssConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 阿里云 OSS 文件上传服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final OSS ossClient;
    private final OssConfig ossConfig;

    /** 头像目录 */
    private static final String AVATAR_DIR = "avatars/";

    /**
     * 上传头像，返回公网访问 URL。
     * 新头像会覆盖旧头像（同 conversation 维度暂不做，每次上传生成新文件名，旧文件异步删除）。
     */
    public String uploadAvatar(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String suffix = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : ".png";
        String key = AVATAR_DIR + "avatar-" + UUID.randomUUID().toString().substring(0, 8) + suffix;

        PutObjectRequest putRequest = new PutObjectRequest(
                ossConfig.getBucketName(), key,
                file.getInputStream(), null);
        ossClient.putObject(putRequest);

        // 拼接公网 URL: https://<bucket>.<endpoint>/<key>
        String url = "https://" + ossConfig.getBucketName() + "."
                + ossConfig.getEndpoint().replace("https://", "").replace("http://", "")
                + "/" + key;

        log.info("头像已上传到 OSS: {}", url);
        return url;
    }

    /**
     * 删除 OSS 文件
     */
    public void delete(String key) {
        ossClient.deleteObject(ossConfig.getBucketName(), key);
        log.info("OSS 文件已删除: {}", key);
    }
}
