package com.jiang.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiang.entity.AgentConfig;
import com.jiang.entity.Document;
import com.jiang.entity.DocumentChunk;
import com.jiang.mapper.AgentConfigMapper;
import com.jiang.mapper.DocumentChunkMapper;
import com.jiang.mapper.DocumentMapper;
import com.jiang.model.PageResult;
import com.jiang.model.req.SearchRequest;
import com.jiang.model.resp.SearchResponse;
import com.jiang.model.vo.DocumentVO;
import com.jiang.util.DeepSeekStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库服务 — RAG 检索 + 文档管理。
 * 覆盖文档上传/解析/分块/向量化、列表、删除、语义检索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DeepSeekStreamService apiClient;
    private final AgentConfigMapper agentConfigMapper;
    private final OssService ossService;
    private final ObjectMapper objectMapper;
    private final VectorStore vectorStore;

    @Qualifier("defaultSystemPrompt")
    private final String defaultSystemPrompt;

    @Value("${spring.ai.openai.chat.model}")
    private String defaultModel;

    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "md", "txt", "docx");
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;
    private static final int CHUNK_SIZE = 800;
    private static final double SIMILARITY_THRESHOLD = 0.5;

    // ==================== 公开接口 ====================

    /**
     * 上传并解析文档 → 分块 → 向量化存入 Qdrant。
     */
    public DocumentVO upload(MultipartFile file) throws IOException {
        // 1. 校验
        String originalFilename = file.getOriginalFilename();
        String ext = getExt(originalFilename).toLowerCase();
        if (!ALLOWED_TYPES.contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型: " + ext + "，允许: " + ALLOWED_TYPES);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过 20MB");
        }

        // 2. SHA-256 去重
        String hash = sha256(file.getBytes());
        Document existing = documentMapper.selectOne(
                new LambdaQueryWrapper<Document>().eq(Document::getContentHash, hash));
        if (existing != null) {
            log.info("文档已存在，跳过上传: {} (hash={})", originalFilename, hash.substring(0, 8));
            return toVO(existing);
        }

        // 3. 上传原始文件到 OSS
        String ossKey;
        try {
            ossKey = ossService.uploadKnowledgeFile(file);
        } catch (Exception e) {
            log.error("OSS 上传失败，继续解析", e);
            ossKey = "";
        }

        // 4. 解析文档内容
        String content = parseFileContent(file, ext);

        // 5. 文本分块
        List<org.springframework.ai.document.Document> chunks = chunkContent(content);

        // 6. 保存文档元数据到 MySQL
        Document doc = new Document();
        doc.setFilename(originalFilename);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setContentHash(hash);
        doc.setChunkCount(chunks.size());
        doc.setStatus(1); // 已解析
        doc.setSummary(content.length() > 200 ? content.substring(0, 200) : content);
        doc.setOssKey(ossKey);
        documentMapper.insert(doc);

        // 7. 保存分片到 MySQL
        for (int i = 0; i < chunks.size(); i++) {
            org.springframework.ai.document.Document chunk = chunks.get(i);
            DocumentChunk entity = new DocumentChunk();
            entity.setDocumentId(doc.getId());
            entity.setChunkIndex(i);
            entity.setContent(chunk.getText());
            entity.setTokenCount(estimateTokens(chunk.getText()));
            documentChunkMapper.insert(entity);

            // 为 Qdrant 添加元数据
            chunk.getMetadata().put("documentId", doc.getId().toString());
            chunk.getMetadata().put("filename", originalFilename);
            chunk.getMetadata().put("chunkIndex", i);
        }

        // 8. 向量化存入 Qdrant
        try {
            vectorStore.add(chunks);
            doc.setStatus(2); // 已向量化
            documentMapper.updateById(doc);
            log.info("文档向量化完成: id={}, 分片数={}", doc.getId(), chunks.size());
        } catch (Exception e) {
            log.error("Qdrant 向量化失败: id={}, error={}", doc.getId(), e.getMessage());
            // 状态仍为 1，可后续重试
        }

        return toVO(doc);
    }

    /** 批量上传文档 */
    public List<DocumentVO> batchUpload(List<MultipartFile> files) {
        List<DocumentVO> results = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                results.add(upload(file));
            } catch (Exception e) {
                log.error("批量上传单文件失败: {}", file.getOriginalFilename(), e);
                // 失败不中断，继续处理剩余文件
                DocumentVO err = new DocumentVO();
                err.setFilename(file.getOriginalFilename());
                err.setStatus(0);
                err.setSummary("上传失败: " + e.getMessage());
                results.add(err);
            }
        }
        return results;
    }

    /** 获取文档的下载 URL */
    public String getDownloadUrl(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) throw new IllegalArgumentException("文档不存在: " + id);
        if (doc.getOssKey() == null || doc.getOssKey().isEmpty())
            throw new IllegalArgumentException("该文档没有存储原始文件");
        return ossService.getPublicUrl(doc.getOssKey());
    }

    /** 分页查询文档列表 */
    public PageResult<DocumentVO> listDocuments(int page, int size) {
        Page<Document> pg = new Page<>(page, size);
        LambdaQueryWrapper<Document> qw = new LambdaQueryWrapper<Document>()
                .orderByDesc(Document::getUploadedAt);
        Page<Document> result = documentMapper.selectPage(pg, qw);
        List<DocumentVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), page, size, records);
    }

    /** 删除文档及其向量和分片 */
    public void deleteDocument(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + id);
        }

        // 1. 从 Qdrant 删除向量（跳过其他文档的 chunks）
        try {
            vectorStore.delete("documentId == '" + id + "'");
            log.info("Qdrant 向量已删除: documentId={}", id);
        } catch (Exception e) {
            log.warn("Qdrant 删除失败（可能已被清理）: documentId={}", id, e);
        }

        // 2. 从 MySQL 删除分片
        documentChunkMapper.delete(
                new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, id));

        // 3. 删除文档主记录
        documentMapper.deleteById(id);

        log.info("文档已删除: id={}, filename={}", id, doc.getFilename());
    }

    /** RAG 语义检索 + LLM 增强回答 */
    public SearchResponse search(SearchRequest req) {
        int topK = req.getTopK() != null ? req.getTopK() : 5;
        String query = req.getQuery();

        // 1. 向量检索
        List<org.springframework.ai.document.Document> hits;
        try {
            hits = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .similarityThreshold(SIMILARITY_THRESHOLD)
                            .build());
        } catch (Exception e) {
            log.error("Qdrant 检索失败", e);
            throw new RuntimeException("知识库检索服务暂不可用，请稍后重试", e);
        }

        // 2. 构建来源列表
        List<SearchResponse.Source> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            org.springframework.ai.document.Document hit = hits.get(i);
            Map<String, Object> meta = hit.getMetadata();
            Long docId = meta.get("documentId") != null
                    ? Long.valueOf(meta.get("documentId").toString()) : null;
            String filename = meta.get("filename") != null
                    ? meta.get("filename").toString() : "未知";
            Integer chunkIdx = meta.get("chunkIndex") instanceof Integer
                    ? (Integer) meta.get("chunkIndex") : null;
            Double score = hit.getScore() != null ? hit.getScore() : 0.0;

            sources.add(new SearchResponse.Source(docId, filename, chunkIdx, hit.getText(), score));
            context.append("--- 文档: ").append(filename)
                    .append(" (相关度: ").append(String.format("%.2f", score)).append(") ---\n")
                    .append(hit.getText()).append("\n\n");
        }

        // 3. 构建增强 Prompt 并调用 LLM
        String systemPrompt = buildRagSystemPrompt();
        String userPrompt = context.isEmpty()
                ? "用户问: " + query + "\n\n（知识库中未找到相关内容，请如实告知用户）"
                : context + "用户问: " + query + "\n\n请根据以上文档片段回答用户问题。如果文档内容无法回答，请如实告知。";

        String answer;
        try {
            String body = buildSearchRequestBody(systemPrompt, userPrompt);
            String resp = apiClient.sync(body);
            answer = objectMapper.readTree(resp)
                    .path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("LLM 检索增强回答失败", e);
            answer = "（AI 回答生成失败: " + e.getMessage() + "）";
        }

        return new SearchResponse(answer, sources);
    }

    // ==================== 内部辅助 ====================

    /** 获取模型名称（DB 优先，否则默认） */
    private String getModel() {
        try {
            AgentConfig config = agentConfigMapper.selectById(1);
            if (config != null && config.getModel() != null && !config.getModel().isBlank()) {
                return config.getModel();
            }
        } catch (Exception ignored) {}
        return defaultModel;
    }

    /** 使用 Apache Tika 解析文档内容（统一处理 PDF/MD/TXT/DOCX） */
    private String parseFileContent(MultipartFile file, String ext) throws IOException {
        // 纯文本类型直接读，避免 Tika 的格式检测开销
        if ("txt".equals(ext) || "md".equals(ext)) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
        // PDF / DOCX 用 Tika 解析
        try (InputStream is = file.getInputStream()) {
            Tika tika = new Tika();
            return tika.parseToString(is);
        } catch (TikaException e) {
            throw new IOException("文档解析失败: " + e.getMessage(), e);
        }
    }

    /** 文本分块 */
    private List<org.springframework.ai.document.Document> chunkContent(String content) {
        var doc = org.springframework.ai.document.Document.builder()
                .text(content)
                .build();
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE)
                .build();
        return splitter.split(List.of(doc));
    }

    /** 构建 RAG 专用的 System Prompt */
    private String buildRagSystemPrompt() {
        return defaultSystemPrompt + "\n\n"
                + "你正在回答一个知识库相关的问题。回答时请注意:\n"
                + "1. 基于提供的文档片段给出准确回答\n"
                + "2. 引用具体的文档名称\n"
                + "3. 如果文档内容不足以回答问题，请如实告知用户";
    }

    /** 构建检索增强的 LLM 请求体 */
    private String buildSearchRequestBody(String systemPrompt, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", getModel());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", 0.3); // 知识问答用低温度

        try {
            return new ObjectMapper().writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("构建检索请求体失败", e);
        }
    }

    /** 估算 token 数量（中文约 1.5 字符/token，英文约 4 字符/token） */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 简单估算：混合文本按平均 2.5 字符/token
        return Math.max(1, text.length() * 2 / 5);
    }

    /** SHA-256 哈希 */
    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    /** 文件扩展名提取 */
    private String getExt(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /** Entity → VO */
    private DocumentVO toVO(Document doc) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setFilename(doc.getFilename());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setChunkCount(doc.getChunkCount());
        vo.setStatus(doc.getStatus());
        vo.setSummary(doc.getSummary());
        vo.setUploadedAt(doc.getUploadedAt());
        vo.setDownloadUrl(
                doc.getOssKey() != null && !doc.getOssKey().isEmpty()
                        ? ossService.getPublicUrl(doc.getOssKey()) : "");
        return vo;
    }
}
