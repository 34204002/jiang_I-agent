package com.jiang.tool;

import com.jiang.model.req.SearchRequest;
import com.jiang.model.resp.SearchResponse;
import com.jiang.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识库检索工具 — 让 Agent 显式搜索知识库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTool {

    private final KnowledgeService knowledgeService;

    @Tool(name = "search_knowledge",
            description = "在知识库中搜索用户上传的文档内容。当用户问「知识库里有没有...」「搜一下...的文档」"
                    + "或问题可能存在于已上传的文档中时使用。",
            parameters = """
                    {
                        "type": "object",
                        "properties": {
                            "query": {"type": "string", "description": "搜索关键词或自然语言问题"},
                            "topK": {"type": "integer", "description": "返回结果数量，默认 5"}
                        },
                        "required": ["query"]
                    }
                    """)
    public String searchKnowledge(String query, Integer topK) {
        SearchRequest req = new SearchRequest();
        req.setQuery(query);
        req.setTopK(topK != null ? topK : 5);
        SearchResponse resp = knowledgeService.search(req);

        if (resp.getSources() == null || resp.getSources().isEmpty()) {
            return "知识库中未找到与「" + query + "」相关的内容。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(resp.getSources().size()).append(" 条相关结果:\n\n");
        for (int i = 0; i < resp.getSources().size(); i++) {
            var s = resp.getSources().get(i);
            sb.append("--- 来源: ").append(s.getFilename())
                    .append(" (相关度: ").append(String.format("%.0f%%", s.getScore() * 100))
                    .append(") ---\n");
            sb.append(s.getContent().length() > 400
                    ? s.getContent().substring(0, 400) + "..."
                    : s.getContent());
            sb.append("\n\n");
        }
        if (resp.getAnswer() != null && !resp.getAnswer().isEmpty()) {
            sb.append("AI 总结: ").append(resp.getAnswer());
        }
        return sb.toString();
    }
}
