package com.visionqc.backend.auth;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CSRF 令牌种植端点：前端在登录/任何写操作前调用一次，
 * 服务端写入 XSRF-TOKEN Cookie，前端读取后放入 X-XSRF-TOKEN 请求头。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
}
