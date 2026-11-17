package com.visionqc.backend.user;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 提供 AuthenticationManager Bean（DaoAuthenticationProvider + 我们的 UserDetailsService）。
 * 放在独立配置类而非 SecurityConfig，职责单一，便于测试替换。
 */
@Service
public class AuthenticationManagerConfig {

    @org.springframework.context.annotation.Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
