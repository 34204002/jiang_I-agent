package com.jiang.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 上传文件临时存储 —— 对话中拖入的文件解析后暂存于此。
 * <p>
 * LLM 通过 {@code read_uploaded_file} 工具读取内容。
 * 文件在首次读取后保留一段时间供重复查阅，JVM 重启后自动清空。
 */
@Slf4j
@Component
public class UploadedFileStore {

    private final Map<String, UploadedFile> store = new ConcurrentHashMap<>();

    /**
     * 存储文件内容，返回唯一 fileId。
     */
    public String put(String filename, String fileType, String content) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        store.put(id, new UploadedFile(filename, fileType, content));
        log.info("文件暂存: id={} filename={} type={} size={}", id, filename, fileType, content.length());
        return id;
    }

    /**
     * 读取文件内容。返回 null 表示文件已过期或不存在。
     */
    public UploadedFile get(String fileId) {
        return store.get(fileId);
    }

    /**
     * 移除文件。
     */
    public void remove(String fileId) {
        UploadedFile removed = store.remove(fileId);
        if (removed != null) {
            log.info("文件已清除: id={} filename={}", fileId, removed.filename);
        }
    }

    public record UploadedFile(String filename, String fileType, String content) {
        public long size() {
            return content != null ? content.length() : 0;
        }
    }
}
