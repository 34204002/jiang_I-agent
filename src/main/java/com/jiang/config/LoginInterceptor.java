package com.jiang.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 登录拦截器。
 * 校验 Authorization header，解析后 setAttribute 供 Controller 使用。
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    /**
     * 拦截请求，校验 JWT Token。
     *
     * <p>校验顺序：OPTIONS 预检放行 → Authorization Header → token Query 参数（EventSource GET 兼容）。
     * 校验通过后将 userId、username、role 写入 request attribute 供 Controller 使用。</p>
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 优先 Header，其次 Query 参数（EventSource 不支持自定义 Header）
        String token = jwtUtil.extractToken(request.getHeader("Authorization"));
        if (token == null) {
            token = request.getParameter("token");
        }
        if (token == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\"}");
            return false;
        }

        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token 过期或无效\"}");
            return false;
        }

        request.setAttribute("userId", claims.get("userId", Long.class));
        request.setAttribute("username", claims.get("username", String.class));
        request.setAttribute("role", claims.get("role", String.class));
        return true;
    }
}
