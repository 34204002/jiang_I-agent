package com.jiang.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiang.entity.Conversation;
import com.jiang.entity.Message;
import com.jiang.mapper.ConversationMapper;
import com.jiang.mapper.MessageMapper;
import com.jiang.model.PageResult;
import com.jiang.model.vo.ConversationVO;
import com.jiang.model.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话历史服务 — MySQL 持久化（Phase 4）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    /**
     * 分页查询会话列表，按更新时间倒序
     */
    public PageResult<ConversationVO> listConversations(int page, int size) {
        Page<Conversation> mpPage = Page.of(page, size);
        Page<Conversation> result = conversationMapper.selectPage(
                mpPage,
                new LambdaQueryWrapper<Conversation>()
                        .orderByDesc(Conversation::getUpdatedAt)
        );

        List<ConversationVO> vos = result.getRecords().stream().map(c -> {
            ConversationVO vo = new ConversationVO();
            vo.setId(c.getId());
            vo.setTitle(c.getTitle());
            vo.setModel(c.getModel());
            vo.setMessageCount(c.getMessageCount());
            vo.setCreatedAt(c.getCreatedAt());
            vo.setUpdatedAt(c.getUpdatedAt());
            return vo;
        }).toList();

        return PageResult.of(result.getTotal(), page, size, vos);
    }

    /**
     * 查询指定会话的消息列表，按时间正序
     */
    public PageResult<MessageVO> listMessages(Long conversationId, int page, int size) {
        Page<Message> mpPage = Page.of(page, size);
        Page<Message> result = messageMapper.selectPage(
                mpPage,
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt)
        );

        List<MessageVO> vos = result.getRecords().stream().map(m -> {
            MessageVO vo = new MessageVO();
            vo.setId(m.getId());
            vo.setRole(m.getRole());
            vo.setContent(m.getContent());
            vo.setTokenCount(m.getTokenCount());
            vo.setCreatedAt(m.getCreatedAt());
            // toolCalls JSON 暂不解析，Phase 4 完善
            return vo;
        }).toList();

        return PageResult.of(result.getTotal(), page, size, vos);
    }

    /**
     * 删除会话（DB 外键 ON DELETE CASCADE 自动级联删除消息）
     */
    public void deleteConversation(Long id) {
        int rows = conversationMapper.deleteById(id);
        if (rows > 0) {
            log.info("会话已删除: id={}", id);
        } else {
            log.warn("删除会话不存在: id={}", id);
        }
    }
}
