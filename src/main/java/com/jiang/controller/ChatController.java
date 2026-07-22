package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.constant.FileConstants;
import com.jiang.model.req.ChatRequest;
import com.jiang.model.resp.ChatResponse;
import com.jiang.service.ChatService;
import com.jiang.service.UploadedFileStore;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对话接口
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final String SSE_UTF8 = "text/event-stream;charset=UTF-8";
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB

    private final ChatService chatService;
    private final UploadedFileStore fileStore;

    /**
     * 同步对话
     */
    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request,
                                     HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        return Result.success(chatService.chat(request, userId));
    }

    /**
     * SSE 流式对话 — GET（支持思考模式）
     */
    @GetMapping(value = "/stream", produces = SSE_UTF8)
    public Flux<String> streamChatGet(@RequestParam String message,
                                      @RequestParam(required = false) String conversationId,
                                      @RequestParam(required = false, defaultValue = "false") boolean thinking,
                                      HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        var request = new ChatRequest(message, conversationId);
        return thinking ? chatService.streamChatWithThinking(request, userId)
                : chatService.streamChat(request, userId);
    }

    /**
     * SSE 流式对话 — POST（支持思考模式，支持附件）
     */
    @PostMapping(value = "/stream", produces = SSE_UTF8)
    public Flux<String> streamChatPost(@RequestBody ChatRequest request,
                                       @RequestParam(required = false, defaultValue = "false") boolean thinking,
                                       HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        return thinking ? chatService.streamChatWithThinking(request, userId)
                : chatService.streamChat(request, userId);
    }

    /**
     * 上传对话附件 — 拖拽文件到聊天框时调用。
     * 后端解析文本内容暂存到内存，返回 fileId。
     * LLM 通过 {@code read_uploaded_file} 工具按需读取文件内容。
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadAttachment(@RequestParam MultipartFile file) {
        if (file.isEmpty()) {
            return Result.fail(400, "文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.fail(413, "文件过大，最大 20MB");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            return Result.fail(400, "不支持的文件类型");
        }
        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!FileConstants.ALLOWED_DOC_TYPES.contains(ext)) {
            return Result.fail(400, "不支持的文件类型: ." + ext + "（支持: pdf/md/txt/docx）");
        }

        // 解析文件内容
        String content;
        try {
            if ("txt".equals(ext) || "md".equals(ext)) {
                content = new String(file.getBytes(), StandardCharsets.UTF_8);
            } else {
                try (InputStream is = file.getInputStream()) {
                    content = new Tika().parseToString(is);
                }
            }
        } catch (TikaException e) {
            return Result.fail(500, "文件解析失败: " + e.getMessage());
        } catch (IOException e) {
            return Result.fail(500, "文件读取失败: " + e.getMessage());
        }

        if (content == null || content.isBlank()) {
            return Result.fail(400, "文件内容为空或无法解析");
        }

        // 截断保护
        int maxLen = 100_000;
        if (content.length() > maxLen) {
            content = content.substring(0, maxLen) + "\n\n（文件过长，已截断至 " + (maxLen / 1000) + "k 字符）";
        }

        // 存入临时存储，返回 fileId
        String fileId = fileStore.put(originalName, ext, content);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileId", fileId);
        data.put("filename", originalName);
        data.put("fileType", ext);
        data.put("size", file.getSize());
        return Result.success(data);
    }

}
