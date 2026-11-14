CREATE TABLE admin_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_user_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_code CHAR(12) NOT NULL,
    title VARCHAR(120) NOT NULL,
    activity_type VARCHAR(32) NOT NULL,
    description TEXT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    location VARCHAR(255) NOT NULL,
    registration_deadline DATETIME NOT NULL,
    capacity INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    livestream_url VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_activity_public_code UNIQUE (public_code),
    CONSTRAINT fk_activity_creator FOREIGN KEY (created_by) REFERENCES admin_user (id),
    CONSTRAINT ck_activity_capacity CHECK (capacity > 0),
    CONSTRAINT ck_activity_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_activity_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    INDEX idx_activity_list (status, starts_at),
    INDEX idx_activity_deadline (registration_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE registration (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    participant_name VARCHAR(64) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    college VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_registration_activity_phone UNIQUE (activity_id, phone),
    CONSTRAINT fk_registration_activity FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT ck_registration_status CHECK (status IN ('REGISTERED', 'CANCELLED')),
    INDEX idx_registration_activity_status (activity_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE check_in (
    id BIGINT NOT NULL AUTO_INCREMENT,
    registration_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    check_in_method VARCHAR(20) NOT NULL DEFAULT 'QR_PHONE',
    checked_in_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_check_in_registration UNIQUE (registration_id),
    CONSTRAINT fk_check_in_registration FOREIGN KEY (registration_id) REFERENCES registration (id),
    CONSTRAINT fk_check_in_activity FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT ck_check_in_method CHECK (check_in_method IN ('QR_PHONE', 'ADMIN_MANUAL')),
    INDEX idx_check_in_activity_time (activity_id, checked_in_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
