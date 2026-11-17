package com.visionqc.backend.user;

import com.visionqc.backend.common.PageResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/** 管理员用户查询（Phase 3 分配质检员用）。只返回展示字段，绝不暴露密码。 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public UserController(UserRepository userRepository,
                          UserRoleRepository userRoleRepository,
                          RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public PageResult<UserSummary> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {

        List<UserSummary> all = userRepository.findAllActive().stream()
                .map(u -> new UserSummary(u.getId(), u.getUsername(), u.getDisplayName(),
                        u.isEnabled(), rolesOf(u.getId())))
                .filter(u -> role == null || u.roles().contains(role))
                .filter(u -> keyword == null || keyword.isBlank()
                        || u.username().toLowerCase().contains(keyword.toLowerCase())
                        || u.displayName().toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toList());

        int from = Math.min(pageable.getPageNumber() * pageable.getPageSize(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageResult<>(all.subList(from, to), all.size(),
                pageable.getPageNumber(), pageable.getPageSize());
    }

    private List<String> rolesOf(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> roleRepository.findById(ur.getRoleId()).map(Role::getCode).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    public record UserSummary(Long id, String username, String displayName,
                              boolean enabled, List<String> roles) {
    }
}
