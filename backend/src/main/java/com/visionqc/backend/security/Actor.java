package com.visionqc.backend.security;

import com.visionqc.backend.common.BusinessException;
import com.visionqc.backend.user.AppUserDetailsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

/**
 * 当前请求操作者：从 Spring Security 的 Authentication 中提取 userId 与角色。
 * 后端所有权限/归属判断都以这里为准，绝不信任前端传来的角色或用户 id。
 */
public record Actor(Long userId, String username, Set<String> roles) {

    public static Actor from(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw BusinessException.forbidden("未登录");
        }
        Long userId = null;
        Object principal = auth.getPrincipal();
        if (principal instanceof AppUserDetailsService.AppUserDetails details) {
            userId = details.userId();
        }
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .collect(java.util.stream.Collectors.toSet());
        return new Actor(userId, auth.getName(), roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isInspector() {
        return hasRole("INSPECTOR");
    }

    public boolean isReviewer() {
        return hasRole("REVIEWER");
    }

    public Long requireUserId() {
        if (userId == null) {
            throw BusinessException.forbidden("当前登录信息缺少用户标识，请重新登录");
        }
        return userId;
    }

    public void requireRole(String role) {
        if (!hasRole(role)) {
            throw BusinessException.forbidden("无权执行该操作，需要 " + role + " 角色");
        }
    }
}
