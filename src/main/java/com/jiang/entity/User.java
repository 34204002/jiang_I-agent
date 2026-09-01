package com.jiang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 — t_user
 */
@Data
@TableName("t_user")
public class User {

    /**
     * 用户主键，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * BCrypt 加密密文
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * ADMIN / USER
     */
    private String role;

    /**
     * 用户自填 DeepSeek API Key（AES-GCM 密文，BYOK；为 null 时用全局 key）
     */
    private String apiKeyEnc;

    /**
     * 用户自选对话模型名（为 null/空格时用全局模型，仅限 DeepSeek 系）
     */
    private String llmModel;

    /**
     * 注册时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
