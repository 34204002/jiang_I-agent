package com.jiang.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.jiang.config.OssConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.UUID;

/**
 * 阿里云 OSS 文件上传服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    /**
     * 头像目录
     */
    private static final String AVATAR_DIR = "avatars/";
    /**
     * 知识库文档目录
     */
    private static final String KNOWLEDGE_DIR = "knowledge/";
    private final OSS ossClient;
    private final OssConfig ossConfig;

    /**
     * 上传头像（公共读桶），返回可直接访问的完整公网 URL。
     */
    public String uploadAvatar(MultipartFile file) throws IOException {
        String key = uploadToDir(publicBucket(), AVATAR_DIR, file);
        return publicUrl(key);
    }

    /**
     * 上传知识库文档原始文件（私有桶），返回 OSS key。
     */
    public String uploadKnowledgeFile(MultipartFile file) throws IOException {
        return uploadToDir(ossConfig.getBucketName(), KNOWLEDGE_DIR, file);
    }

    private String uploadToDir(String bucket, String dir, MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String suffix = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        String key = dir + UUID.randomUUID().toString().substring(0, 8) + suffix;

        PutObjectRequest putRequest = new PutObjectRequest(
                bucket, key,
                file.getInputStream(), null);
        ossClient.putObject(putRequest);

        log.info("OSS 上传成功: bucket={}, key={}", bucket, key);
        return key;
    }

    /**
     * 按 key 下载文档原始字节（私有桶，异步消费者取回文件用）。
     */
    public byte[] download(String key) throws IOException {
        if (key == null || key.isEmpty()) {
            throw new IOException("OSS key 为空，无法下载");
        }
        OSSObject obj = ossClient.getObject(ossConfig.getBucketName(), key);
        try (InputStream in = obj.getObjectContent()) {
            return in.readAllBytes();
        }
    }

    /**
     * 删除 OSS 文件（私有桶，删除用户文档）
     */
    public void delete(String key) {
        ossClient.deleteObject(ossConfig.getBucketName(), key);
        log.info("OSS 文件已删除: {}", key);
    }

    /**
     * 私有桶文档的预签名 URL（默认 1 小时有效）。
     * 私有桶对象不能用公网直链，必须用签名 URL 才能让用户下载自己的文档。
     */
    public String generatePresignedUrl(String key) {
        if (key == null || key.isEmpty()) return "";
        try {
            var exp = new Date(System.currentTimeMillis() + 3600_000L);
            return ossClient.generatePresignedUrl(ossConfig.getBucketName(), key, exp).toString();
        } catch (Exception e) {
            log.warn("生成预签名 URL 失败: {}", key, e);
            return "";
        }
    }

    /**
     * 公共读桶的公网 URL（头像/演示图等公开资源直接可访问）。
     */
    public String getPublicUrl(String key) {
        if (key == null || key.isEmpty()) return "";
        return publicUrl(key);
    }

    /** 公共读桶名（未配置 publicBucketName 时回退到默认私有桶，保证兼容） */
    private String publicBucket() {
        String p = ossConfig.getPublicBucketName();
        return p != null && !p.isBlank() ? p : ossConfig.getBucketName();
    }

    /** 拼接公共读桶的完整公网 URL */
    private String publicUrl(String key) {
        return "https://" + publicBucket() + "."
                + ossConfig.getEndpoint().replace("https://", "").replace("http://", "")
                + "/" + key;
    }
}
