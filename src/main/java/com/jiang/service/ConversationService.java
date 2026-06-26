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
     * 分页查询当前用户会话列表，按更新时间倒序
     */
    public PageResult<ConversationVO> listConversations(Long userId, int page, int size) {
        Page<Conversation> mpPage = Page.of(page, size);
        Page<Conversation> result = conversationMapper.selectPage(
                mpPage,
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
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
     * 查询会话消息列表（校验归属），按时间正序
     */
    public PageResult<MessageVO> listMessages(Long conversationId, Long userId, int page, int size) {
        Conversation convo = conversationMapper.selectById(conversationId);
        if (convo == null || !convo.getUserId().equals(userId)) {
            log.warn("越权访问会话: userId={}, conversationId={}", userId, conversationId);
            throw new SecurityException("无权访问该会话");
        }

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
            vo.setThinking(m.getThinking());
            vo.setTokenCount(m.getTokenCount());
            vo.setCreatedAt(m.getCreatedAt());
            return vo;
        }).toList();

        return PageResult.of(result.getTotal(), page, size, vos);
    }

    /**
     * 删除会话（校验归属，DB 外键 ON DELETE CASCADE 级联删除消息）
     */
    public void deleteConversation(Long id, Long userId) {
        Conversation convo = conversationMapper.selectById(id);
        if (convo == null) {
            log.warn("删除会话不存在: id={}", id);
            return;
        }
        if (!convo.getUserId().equals(userId)) {
            log.warn("越权删除会话: userId={}, conversationId={}", userId, id);
            throw new SecurityException("无权删除该会话");
        }
        conversationMapper.deleteById(id);
        log.info("会话已删除: id={}", id);
    }

    /**
     * 批量删除会话（逐一校验归属，跳过无权或已删除的）。
     * @return 实际删除的数量
     */
    public int deleteConversations(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) return 0;
        int deleted = 0;
        for (Long id : ids) {
            try {
                deleteConversation(id, userId);
                deleted++;
            } catch (SecurityException e) {
                log.warn("批量删除跳过: id={}, reason={}", id, e.getMessage());
            }
        }
        log.info("批量删除完成: {} / {} 个会话", deleted, ids.size());
        return deleted;
    }
}
