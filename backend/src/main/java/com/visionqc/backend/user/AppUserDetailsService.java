package com.visionqc.backend.user;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证桥接：把业务 users/user_roles 表接入 Spring Security。
 * principal = userId（Long），details = displayName，供 AuthController 组装 CurrentUser。
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public AppUserDetailsService(UserRepository userRepository,
                                  UserRoleRepository userRoleRepository,
                                  RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));

        // 只查该用户的角色关联，避免全表扫 user_roles
        List<Long> roleIds = userRoleRepository.findAll().stream()
                .filter(ur -> ur.getUserId().equals(user.getId()))
                .map(UserRole::getRoleId)
                .toList();

        List<SimpleGrantedAuthority> authorities = roleRepository.findAllById(roleIds).stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getCode()))
                .toList();

        return new AppUserDetails(user.getId(), user.getUsername(), user.getPassword(),
                user.isEnabled(), user.getDisplayName(), authorities);
    }

    /** UserDetails 实现类：principal 放 userId */
    public record AppUserDetails(Long userId, String username, String password,
                                 boolean enabled, String displayName,
                                 List<SimpleGrantedAuthority> authorities) implements UserDetails {

        /** UserDetails 接口要求 getAuthorities()，record 组件访问器是 authorities()，这里补齐桥接 */
        public List<SimpleGrantedAuthority> getAuthorities() { return authorities; }

        @Override
        public String getPassword() { return password; }
        @Override
        public String getUsername() { return username; }
        @Override
        public boolean isAccountNonExpired() { return true; }
        @Override
        public boolean isAccountNonLocked() { return true; }
        @Override
        public boolean isCredentialsNonExpired() { return true; }
        @Override
        public boolean isEnabled() { return enabled; }
    }
}
