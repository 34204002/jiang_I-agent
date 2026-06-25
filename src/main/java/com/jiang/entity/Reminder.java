package com.jiang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_reminder")
public class Reminder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String message;
    private LocalDateTime remindAt;
    private Integer fired; // 0-未触发 1-已触发

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
