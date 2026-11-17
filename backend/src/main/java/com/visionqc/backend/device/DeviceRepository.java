package com.visionqc.backend.device;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Page<Device> findByDeletedFalse(Pageable pageable);

    Page<Device> findByDeletedFalseAndDeviceNameContainingIgnoreCaseOrDeletedFalseAndDeviceCodeContainingIgnoreCase(
            String nameKey, String nameKey2, String codeKey, String codeKey2, Pageable pageable);

    Optional<Device> findByIdAndDeletedFalse(Long id);

    boolean existsByDeviceCodeAndDeletedFalse(String deviceCode);

    /** Spring Data 派生查询对 OR + 参数重复写法脆弱，这里用自定义 JPQL 更直观（见 DeviceServiceImpl 用法） */
    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM Device d
            WHERE d.deleted = false
              AND (LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(d.deviceCode) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Device> search(@org.springframework.data.repository.query.Param("keyword") String keyword, Pageable pageable);
}
