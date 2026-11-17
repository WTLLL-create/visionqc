-- VisionQC V1 全量建表（Flyway 管理的设计见 docs/02-数据库设计.md）
-- 字符集统一 utf8mb4，时间用 DATETIME(6)

CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    display_name VARCHAR(50) NOT NULL,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    code VARCHAR(20)  NOT NULL,
    name VARCHAR(50)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE devices (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    device_name  VARCHAR(100) NOT NULL,
    device_code  VARCHAR(50)  NOT NULL,
    device_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    description  VARCHAR(255) NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_devices_code (device_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE production_batches (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    batch_code   VARCHAR(50)  NOT NULL,
    device_id    BIGINT       NOT NULL,
    product_type VARCHAR(100) NOT NULL,
    capture_date DATE         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at   DATETIME(6)  NOT NULL,
    deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batches_code (batch_code),
    CONSTRAINT fk_batch_device FOREIGN KEY (device_id) REFERENCES devices (id),
    INDEX idx_batch_device (device_id, capture_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE image_assets (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    batch_id      BIGINT       NOT NULL,
    file_key      VARCHAR(120) NOT NULL COMMENT '服务端生成 {uuid}.{ext}',
    original_name VARCHAR(255) NOT NULL,
    sha256        CHAR(64)     NOT NULL,
    width         INT          NOT NULL,
    height        INT          NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_img_file_key (file_key),
    UNIQUE KEY uk_batch_sha (batch_id, sha256),
    CONSTRAINT fk_img_batch FOREIGN KEY (batch_id) REFERENCES production_batches (id),
    INDEX idx_img_batch (batch_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE defect_categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_defect_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inspection_tasks (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    image_id     BIGINT      NOT NULL,
    inspector_id BIGINT      NULL,
    reviewer_id  BIGINT      NULL,
    status       VARCHAR(20) NOT NULL,
    version      INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    remark       VARCHAR(500) NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_image (image_id),
    CONSTRAINT fk_task_image FOREIGN KEY (image_id) REFERENCES image_assets (id),
    CONSTRAINT fk_task_inspector FOREIGN KEY (inspector_id) REFERENCES users (id),
    CONSTRAINT fk_task_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
    INDEX idx_task_status (status),
    INDEX idx_task_inspector (inspector_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE annotations (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    task_id     BIGINT        NOT NULL,
    category_id BIGINT        NOT NULL,
    x_norm      DECIMAL(8,6)  NOT NULL,
    y_norm      DECIMAL(8,6)  NOT NULL,
    w_norm      DECIMAL(8,6)  NOT NULL,
    h_norm      DECIMAL(8,6)  NOT NULL,
    img_width   INT           NOT NULL,
    img_height  INT           NOT NULL,
    note        VARCHAR(255)  NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ann_task FOREIGN KEY (task_id) REFERENCES inspection_tasks (id),
    CONSTRAINT fk_ann_category FOREIGN KEY (category_id) REFERENCES defect_categories (id),
    INDEX idx_ann_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE review_records (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    task_id     BIGINT       NOT NULL,
    operator_id BIGINT       NOT NULL,
    action      VARCHAR(20)  NOT NULL,
    from_status VARCHAR(20)  NOT NULL,
    to_status   VARCHAR(20)  NOT NULL,
    comment     VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_task FOREIGN KEY (task_id) REFERENCES inspection_tasks (id),
    CONSTRAINT fk_review_user FOREIGN KEY (operator_id) REFERENCES users (id),
    INDEX idx_review_task (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    operator_id BIGINT       NULL,
    action      VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   BIGINT       NULL,
    detail      VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_user FOREIGN KEY (operator_id) REFERENCES users (id),
    INDEX idx_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子数据：角色字典（代码侧通过 seed-admin 建 admin 用户，密码 BCrypt）
INSERT INTO roles (code, name) VALUES
    ('ADMIN', '管理员'),
    ('INSPECTOR', '质检员'),
    ('REVIEWER', '审核员');
