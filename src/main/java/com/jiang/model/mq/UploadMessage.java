package com.jiang.model.mq;

/**
 * 文档异步处理消息 — 队列里只传「身份凭证」，文件字节始终留在 OSS。
 * 消费者收到后用 {@code docId} 查库拿 ossKey，再回 OSS 取回字节。
 *
 * @param docId  待处理文档 ID（t_document.id）
 * @param userId 归属用户（隔离 & 追溯）
 * @param hash   SHA-256 内容指纹（日志/排查用）
 */
public record UploadMessage(Long docId, Long userId, String hash) {
}