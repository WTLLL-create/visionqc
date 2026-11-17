package com.visionqc.backend.support;

import com.visionqc.backend.batch.BatchRepository;
import com.visionqc.backend.batch.ProductionBatch;
import com.visionqc.backend.device.Device;
import com.visionqc.backend.device.DeviceRepository;
import com.visionqc.backend.user.AppUserDetailsService;
import com.visionqc.backend.user.Role;
import com.visionqc.backend.user.RoleRepository;
import com.visionqc.backend.user.User;
import com.visionqc.backend.user.UserRepository;
import com.visionqc.backend.user.UserRole;
import com.visionqc.backend.user.UserRoleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** 集成测试支撑：生成真实图片字节 + 业务数据/用户构造 + 表清理 */
@Component
public class TestSupport {

    private final DeviceRepository deviceRepository;
    private final BatchRepository batchRepository;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public TestSupport(DeviceRepository deviceRepository, BatchRepository batchRepository,
                       JdbcTemplate jdbcTemplate, UserRepository userRepository,
                       UserRoleRepository userRoleRepository, RoleRepository roleRepository) {
        this.deviceRepository = deviceRepository;
        this.batchRepository = batchRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    /** 生成指定宽高的真实 PNG（ImageIO 可解析，用于通过服务端格式与尺寸校验） */
    public static byte[] pngBytes(int width, int height) throws Exception {
        return imageBytes(width, height, "png");
    }

    public static byte[] jpegBytes(int width, int height) throws Exception {
        return imageBytes(width, height, "jpg");
    }

    private static byte[] imageBytes(int width, int height, String format) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                img.setRGB(x, y, (x * 255 / width) << 16 | (y * 255 / height) << 8);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** 在 JPEG 的 SOI 后插入一个 EXIF APP1 段，用于验证方向标记拒绝策略 */
    public static byte[] withExifOrientation(byte[] jpeg, int orientation) throws Exception {
        byte[] tiff = new byte[]{
                'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,
                0x01, 0x00,
                0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00,
                (byte) (orientation & 0xFF), 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
        byte[] payload = new byte[6 + tiff.length];
        payload[0] = 'E'; payload[1] = 'x'; payload[2] = 'i'; payload[3] = 'f';
        payload[4] = 0; payload[5] = 0;
        System.arraycopy(tiff, 0, payload, 6, tiff.length);
        int segmentLength = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1);
        out.write((segmentLength >> 8) & 0xFF);
        out.write(segmentLength & 0xFF);
        out.write(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    public ProductionBatch createBatch() {
        Device device = new Device();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        device.setDeviceName("测试相机-" + suffix);
        device.setDeviceCode("CAM-" + suffix);
        deviceRepository.save(device);

        ProductionBatch batch = new ProductionBatch();
        batch.setBatchCode("B-" + suffix);
        batch.setDeviceId(device.getId());
        batch.setProductType("TEST-PART");
        batch.setCaptureDate(LocalDate.now());
        return batchRepository.save(batch);
    }

    /** 创建带指定角色的测试用户，返回实体（含真实 userId） */
    public User createUser(String roleCode) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("test-" + suffix);
        user.setPassword("not-used-in-tests");
        user.setDisplayName("测试" + roleCode + "-" + suffix);
        user.setEnabled(true);
        userRepository.save(user);

        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("角色不存在: " + roleCode));
        userRoleRepository.save(new UserRole(user.getId(), role.getId()));
        return user;
    }

    /** 构造与真实登录 principal 一致的 UserDetails，供 MockMvc 使用 */
    public static AppUserDetailsService.AppUserDetails appUser(User user, String roleCode) {
        return new AppUserDetailsService.AppUserDetails(
                user.getId(), user.getUsername(), user.getPassword(), true, user.getDisplayName(),
                List.of(new SimpleGrantedAuthority("ROLE_" + roleCode)));
    }

    /** 清理业务表，避免测试间互相干扰（保留 roles 字典） */
    public void cleanBusinessTables() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        for (String table : new String[]{"annotations", "review_records", "audit_logs",
                "inspection_tasks", "image_assets", "production_batches", "devices",
                "user_roles", "users"}) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
    }
}
