package com.visionqc.backend.user;

import java.io.Serializable;

/** user_roles 复合主键 */
public class UserRoleId implements Serializable {
    private Long userId;
    private Long roleId;

    public UserRoleId() {}

    public UserRoleId(Long userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserRoleId that)) return false;
        return userId.equals(that.userId) && roleId.equals(that.roleId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(userId, roleId);
    }
}
