package com.visionqc.backend.user;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 开发环境种子数据：首次启动若无任何用户，创建管理员账号（BCrypt）。
 * 密码由 APP_ADMIN_PASSWORD / APP_INSPECTOR_PASSWORD / APP_REVIEWER_PASSWORD 注入，
 * 默认值仅为占位符，生产环境必须关闭种子或改成强密码。
 */
@Service
public class SeedDataService {

    private static final Logger log = LoggerFactory.getLogger(SeedDataService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Value("${app.seed-admin:true}")
    private boolean seedAdmin;

    @Value("${app.seed-demo:false}")
    private boolean seedDemo;

    @Value("${app.seed-admin-password:change-me-admin}")
    private String adminPassword;

    @Value("${app.seed-inspector-password:change-me-inspector}")
    private String inspectorPassword;

    @Value("${app.seed-reviewer-password:change-me-reviewer}")
    private String reviewerPassword;

    public SeedDataService(UserRepository userRepository,
                           RoleRepository roleRepository,
                           UserRoleRepository userRoleRepository,
                           org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    @Transactional
    public void seed() {
        if (!seedAdmin) {
            return;
        }
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setDisplayName("系统管理员");
            userRepository.save(admin);
            assignRole(admin, "ADMIN");
            log.info("Seeded initial admin user: admin (password from APP_ADMIN_PASSWORD)");
        }

        // 演示账号：质检员/审核员，方便端到端验收（由 app.seed-demo 控制）
        if (seedDemo) {
            createDemoUserIfAbsent("inspector01", inspectorPassword, "质检员一号", "INSPECTOR");
            createDemoUserIfAbsent("reviewer01", reviewerPassword, "审核员一号", "REVIEWER");
            createDemoUserIfAbsent("reviewer02", reviewerPassword, "审核员二号", "REVIEWER");
        }
    }

    private void createDemoUserIfAbsent(String username, String rawPassword, String displayName, String roleCode) {
        if (userRepository.findByUsernameAndDeletedFalse(username).isPresent()) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        userRepository.save(user);
        assignRole(user, roleCode);
        log.info("Seeded demo user: {} (仅限本地演示)", username);
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("角色表缺少 " + roleCode + "，请检查 Flyway 迁移"));
        userRoleRepository.save(new UserRole(user.getId(), role.getId()));
    }
}
