package com.visionqc.backend.auth;

import java.util.List;

public record CurrentUser(
        Long id,
        String username,
        String displayName,
        List<String> roles) {
}
