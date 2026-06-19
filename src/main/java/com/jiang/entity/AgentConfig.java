package com.jiang.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 全局配置实体 — t_agent_config
 */
@Data
@TableName("t_agent_config")
public class AgentConfig {

    /** 主键，固定为 1 */
    @TableId
    private Integer id;

    /** Agent 名称 */
    private String agentName;

    /** Agent 头像 URL */
    private String avatar;

    /** 系统提示词 */
    private String systemPrompt;

    /** 默认模型 */
    private String model;

    /** 温度参数 */
    private Double temperature;

    /** 最后更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
