package com.jiang.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiang.entity.Reminder;
import com.jiang.mapper.ReminderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时提醒服务 — 管理提醒的创建、查询、到期触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional
public class ReminderService {

    private final ReminderMapper reminderMapper;

    public Reminder create(Long userId, String message, LocalDateTime remindAt) {
        Reminder r = new Reminder();
        r.setUserId(userId);
        r.setMessage(message);
        r.setRemindAt(remindAt);
        r.setFired(0);
        reminderMapper.insert(r);
        log.info("提醒已创建: id={}, userId={}, remindAt={}", r.getId(), userId, remindAt);
        return r;
    }

    public List<Reminder> listByUser(Long userId, boolean pendingOnly) {
        LambdaQueryWrapper<Reminder> qw = new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getUserId, userId);
        if (pendingOnly) qw.eq(Reminder::getFired, 0);
        qw.orderByAsc(Reminder::getRemindAt);
        return reminderMapper.selectList(qw);
    }

    public void cancel(Long userId, Long id) {
        Reminder r = reminderMapper.selectById(id);
        if (r != null && r.getUserId().equals(userId)) {
            reminderMapper.deleteById(id);
            log.info("提醒已取消: id={}", id);
        }
    }

    /**
     * 标记提醒为已触发（前端弹通知后确认）。
     * <p>
     * 说明：不再用后端定时任务自动置 fired —— 那会在前端看到之前就把提醒标记掉，
     * 导致通知永远无法送达。改为前端轮询 pending 提醒、到期弹提示后调用此接口确认。
     * 离线错过的提醒会一直保持 pending，下次打开应用时补弹。幂等：仅未触发的会更新。
     */
    public void ack(Long userId, Long id) {
        Reminder r = reminderMapper.selectById(id);
        if (r != null && r.getUserId().equals(userId)
                && r.getFired() != null && r.getFired() == 0) {
            r.setFired(1);
            reminderMapper.updateById(r);
            log.info("提醒已确认: id={}, message={}", id, r.getMessage());
        }
    }
}
