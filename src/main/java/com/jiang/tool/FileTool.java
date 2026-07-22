package com.jiang.tool;

import com.jiang.service.UploadedFileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件工具 — 让 LLM 自主读取对话中用户上传的文件内容。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileTool {

    private final UploadedFileStore fileStore;

    @Tool(name = "read_uploaded_file",
            description = "读取用户在当前对话中上传的文件内容。当你需要查看用户上传的文档全文、"
                    + "定位文档中的具体段落、或回答基于文件内容的问题时调用此工具。",
            parameters = """
                    {"type":"object","properties":{
                      "fileId":{"type":"string","description":"文件ID，从对话上下文中获取"}
                    },"required":["fileId"]}
                    """)
    public String readUploadedFile(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return "错误: 未指定 fileId";
        }
        UploadedFileStore.UploadedFile file = fileStore.get(fileId);
        if (file == null) {
            return "错误: 文件不存在或已过期（fileId: " + fileId + "）。请告知用户重新上传。";
        }
        log.info("LLM 读取文件: id={} filename={} size={}", fileId, file.filename(), file.size());
        // 截断保护
        String content = file.content();
        int maxLen = 80_000;
        if (content != null && content.length() > maxLen) {
            content = content.substring(0, maxLen)
                    + "\n\n（文件过长，已截断至" + (maxLen / 1000) + "k 字符。如需完整内容请告知用户。）";
        }
        return "【文件: " + file.filename() + " (." + file.fileType() + ")】\n" + (content != null ? content : "(空文件)");
    }
}
