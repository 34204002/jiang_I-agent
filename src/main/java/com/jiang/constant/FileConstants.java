package com.jiang.constant;

import java.util.Set;

/**
 * 文件上传相关常量
 */
public final class FileConstants {
    /**
     * 允许上传的文档类型
     */
    public static final Set<String> ALLOWED_DOC_TYPES = Set.of("pdf", "md", "txt", "docx");
    /**
     * 文档上传最大大小：20MB
     */
    public static final long MAX_DOC_SIZE = 20 * 1024 * 1024;
    /**
     * 文档分块大小
     */
    public static final int CHUNK_SIZE = 800;
    /**
     * 向量检索相似度阈值
     */
    public static final double SIMILARITY_THRESHOLD = 0.5;
    /**
     * 头像上传最大大小：2MB
     */
    public static final long AVATAR_MAX_SIZE = 2 * 1024 * 1024;
    /**
     * 允许的图片类型
     */
    public static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private FileConstants() {
    }
}
