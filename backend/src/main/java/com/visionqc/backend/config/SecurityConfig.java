package com.visionqc.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * 安全配置。
 *
 * 认证方案（任务书 6.1）：单体部署、前后端同源，选择 Session 认证而非 JWT：
 * - 登出立即失效、实现简单、Spring Security 原生支持；
 * - JWT 适合网关后多服务无状态部署，本项目引入只会增加复杂度。
 *
 * CSRF：Session Cookie 认证必须防 CSRF。使用 CookieCsrfTokenRepository（XSRF-TOKEN 可读 Cookie）
 * + 前端 X-XSRF-TOKEN 请求头，Double Submit 模式；登录前先 GET /api/v1/auth/csrf 种植令牌。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                // 令牌放可读 Cookie，前端读取后写入 X-XSRF-TOKEN 头（同源部署，Double Submit）
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // 使用默认请求处理器，保证 Cookie 与请求头按原值比对（不启用 XOR 混淆）
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                // 仅放开获取令牌端点（该端点自身负责种植令牌）
                .ignoringRequestMatchers("/api/v1/auth/csrf"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/csrf").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // ---- 后端强制的权限规则：前端隐藏按钮只是体验优化（任务书第五节）----
                .requestMatchers("/api/v1/devices/**", "/api/v1/users/**").hasRole("ADMIN")
                // 批次读取供审核工作台/质检任务筛选使用；创建/关闭仍仅管理员
                .requestMatchers(HttpMethod.GET, "/api/v1/batches/**")
                    .hasAnyRole("INSPECTOR", "REVIEWER", "ADMIN")
                .requestMatchers("/api/v1/batches/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/images/upload/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/images/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/images/**", "/api/v1/defect-categories/**").authenticated()
                // ---- Phase 3 状态机动作：按动作而不是按路径前缀授权，避免越权提交/审批 ----
                .requestMatchers(HttpMethod.POST, "/api/v1/tasks/*/assign").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/tasks/*/approve",
                                 "/api/v1/tasks/*/reject").hasRole("REVIEWER")
                .requestMatchers(HttpMethod.POST, "/api/v1/tasks/*/start",
                                 "/api/v1/tasks/*/submit").hasRole("INSPECTOR")
                .requestMatchers(HttpMethod.PUT, "/api/v1/tasks/*/annotations").hasRole("INSPECTOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/tasks/**")
                    .hasAnyRole("INSPECTOR", "REVIEWER", "ADMIN")
                .requestMatchers("/api/v1/tasks/**").hasAnyRole("INSPECTOR", "REVIEWER", "ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionFixation(f -> f.newSession()) // 登录换新 Session，防会话固定攻击
                .maximumSessions(1)                     // 同账号单点登录
            );
        return http.build();
    }
}
