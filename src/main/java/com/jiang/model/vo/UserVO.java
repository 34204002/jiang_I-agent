package com.jiang.model.vo;

import com.jiang.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户视图（不含密码）
 */
@Data
public class UserVO {
    /**
     * 用户 ID
     */
    private Long id;
    /**
     * 用户名
     */
    private String username;
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
     * 用户自选对话模型名（BYOK；空=用全局模型）
     */
    private String llmModel;
    /**
     * 已配置 API Key 的脱敏回显（****后四位）；未配置为 null。绝不返回明文/密文
     */
    private String apiKeyMasked;
    /**
     * 注册时间
     */
    private LocalDateTime createdAt;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setLlmModel(user.getLlmModel());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
