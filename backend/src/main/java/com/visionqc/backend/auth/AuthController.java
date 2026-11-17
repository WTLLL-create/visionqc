package com.visionqc.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.visionqc.backend.common.BusinessException;
import com.visionqc.backend.user.AppUserDetailsService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /** 登录：成功后 Spring Security 将认证写入 Session（JSESSIONID Cookie） */
    @PostMapping("/login")
    public CurrentUser login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));

            // 登录成功后显式更新 SecurityContext 到新 Session，防会话固定
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession newSession = request.getSession(true);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            newSession.setAttribute("SPRING_SECURITY_CONTEXT", context);

            return toCurrentUser(auth);
        } catch (AuthenticationException e) {
            // 统一模糊提示，不暴露"用户不存在"还是"密码错误"
            throw BusinessException.badRequest("用户名或密码错误");
        }
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    public CurrentUser me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw BusinessException.forbidden("未登录");
        }
        return toCurrentUser(auth);
    }

    private CurrentUser toCurrentUser(Authentication auth) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .toList();
        // Spring Security 6 的 DaoAuthenticationProvider 将 principal 设为 UserDetails 本身，
        // 类型安全提取 userId，避免强转 ClassCastException
        Long userId;
        Object principal = auth.getPrincipal();
        if (principal instanceof AppUserDetailsService.AppUserDetails details) {
            userId = details.userId();
        } else {
            userId = null;
        }
        String displayName = principal instanceof AppUserDetailsService.AppUserDetails details
                ? details.displayName() : auth.getName();
        return new CurrentUser(userId, auth.getName(), displayName, roles);
    }
}
