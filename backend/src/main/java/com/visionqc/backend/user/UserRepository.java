package com.visionqc.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameAndDeletedFalse(String username);

    @Query("SELECT u FROM User u WHERE u.deleted = false ORDER BY u.id")
    List<User> findAllActive();

    @Query("""
            SELECT u.id AS userId
            FROM User u, UserRole ur, Role r
            WHERE ur.userId = u.id AND ur.roleId = r.id
              AND r.code = :code AND u.deleted = false AND u.enabled = true
            """)
    List<Long> findIdsByRoleCode(@Param("code") String code);
}
