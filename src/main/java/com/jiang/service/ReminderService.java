package com.jiang.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiang.entity.Reminder;
import com.jiang.mapper.ReminderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
     * 每分钟检查一次，标记到期的提醒为已触发。
     */
    @Scheduled(fixedRate = 60_000)
    public void fireDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> due = reminderMapper.selectList(
                new LambdaQueryWrapper<Reminder>()
                        .eq(Reminder::getFired, 0)
                        .le(Reminder::getRemindAt, now));
        for (Reminder r : due) {
            r.setFired(1);
            reminderMapper.updateById(r);
            log.info("提醒到期触发: id={}, message={}", r.getId(), r.getMessage());
        }
    }
}
