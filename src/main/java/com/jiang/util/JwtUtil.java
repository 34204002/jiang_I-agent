package com.jiang.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发 & 校验工具
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMs;

    public JwtUtil(@Value("${app.jwt.secret:jiang-i-agent-default-key-please-change-in-dev-yml}") String secret,
                   @Value("${app.jwt.expire-days:7}") long expireDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireDays * 24 * 3600 * 1000L;
    }

    /**
     * 生成 Token
     */
    public String generate(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 Token，失败返回 null
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Bearer header 提取 Token
     */
    public String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
