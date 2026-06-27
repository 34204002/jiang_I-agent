package com.jiang.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册拦截器 + CORS（先限流、后鉴权）。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final LoginInterceptor loginInterceptor;

    @Override
    public void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流 — 拦截所有 /api/**（先过滤）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register");

        // 鉴权 — 需要登录的路径
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(
                        "/api/user/**",
                        "/api/admin/**",
                        "/api/chat/**",
                        "/api/conversations/**",
                        "/api/knowledge/**",
                        "/api/tools/**",
                        "/api/todos/**",
                        "/api/graph/**",
                        "/api/profile/**"
                )
                .excludePathPatterns(
                        "/api/admin/agent/profile"
                );
    }
}
