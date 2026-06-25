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
    /** 知识库文档目录 */
    private static final String KNOWLEDGE_DIR = "knowledge/";

    /**
     * 上传头像，返回公网访问 URL。
     */
    public String uploadAvatar(MultipartFile file) throws IOException {
        return uploadToDir(AVATAR_DIR, file);
    }

    /**
     * 上传知识库文档原始文件，返回 OSS key。
     */
    public String uploadKnowledgeFile(MultipartFile file) throws IOException {
        return uploadToDir(KNOWLEDGE_DIR, file);
    }

    private String uploadToDir(String dir, MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String suffix = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        String key = dir + UUID.randomUUID().toString().substring(0, 8) + suffix;

        PutObjectRequest putRequest = new PutObjectRequest(
                ossConfig.getBucketName(), key,
                file.getInputStream(), null);
        ossClient.putObject(putRequest);

        log.info("OSS 上传成功: {}", key);
        return key;
    }

    /**
     * 删除 OSS 文件
     */
    public void delete(String key) {
        ossClient.deleteObject(ossConfig.getBucketName(), key);
        log.info("OSS 文件已删除: {}", key);
    }

    /**
     * 返回 OSS 文件的公网访问地址。
     */
    public String getPublicUrl(String key) {
        if (key == null || key.isEmpty()) return "";
        return "https://" + ossConfig.getBucketName() + "."
                + ossConfig.getEndpoint().replace("https://", "").replace("http://", "")
                + "/" + key;
    }
}
