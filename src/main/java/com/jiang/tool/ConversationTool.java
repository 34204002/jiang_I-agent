package com.jiang.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiang.entity.Message;
import com.jiang.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话工具 — 搜索历史消息、导出会话。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationTool {

    private final MessageMapper messageMapper;

    @Tool(name = "search_conversation",
          description = "在当前会话中搜索历史消息。当用户问「我之前聊过的...」「之前说的那个...」「帮我找之前的对话」时使用。",
          parameters = """
              {
                  "type": "object",
                  "properties": {
                      "keyword": {"type": "string", "description": "搜索关键词（必填）"},
                      "limit": {"type": "integer", "description": "最多返回条数，默认 5"}
                  },
                  "required": ["keyword"]
              }
              """)
    public String searchConversation(String keyword, Integer limit) {
        Long convoId = ToolContext.getConversation();
        if (convoId == null) {
            return "当前没有活跃的会话";
        }

        int max = limit != null && limit > 0 ? Math.min(limit, 10) : 5;
        List<Message> msgs = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, convoId)
                        .like(Message::getContent, keyword)
                        .orderByDesc(Message::getCreatedAt)
                        .last("LIMIT " + max));

        if (msgs.isEmpty()) {
            return "在当前会话中没有找到包含「" + keyword + "」的消息。";
        }

        return msgs.stream()
                .map(m -> "[" + m.getRole() + " @ " + m.getCreatedAt() + "] "
                        + (m.getContent().length() > 300
                                ? m.getContent().substring(0, 300) + "..."
                                : m.getContent()))
                .collect(Collectors.joining("\n\n"));
    }

    @Tool(name = "export_conversation",
          description = "导出当前对话为 Markdown 格式。当用户说「导出对话」「保存对话」「整理成文档」时使用。",
          parameters = "{}")
    public String exportConversation() {
        Long convoId = ToolContext.getConversation();
        if (convoId == null) {
            return "当前没有活跃的会话";
        }

        List<Message> msgs = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, convoId)
                        .orderByAsc(Message::getCreatedAt));

        if (msgs.isEmpty()) {
            return "当前会话没有消息";
        }

        StringBuilder md = new StringBuilder();
        md.append("# 对话导出\n\n");
        md.append("> 导出时间: ").append(java.time.LocalDateTime.now()).append("\n\n---\n\n");

        for (Message msg : msgs) {
            String roleLabel = "user".equals(msg.getRole()) ? "👤 用户" : "🤖 Agent";
            md.append("### ").append(roleLabel).append("\n\n");
            md.append(msg.getContent()).append("\n\n---\n\n");
        }

        log.info("对话已导出: convoId={}, 消息数={}, 长度={}", convoId, msgs.size(), md.length());
        return md.toString();
    }
}
