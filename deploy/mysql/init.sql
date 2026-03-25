-- =============================================================
-- Xunrana Blog Database Initialization Script
-- =============================================================

CREATE DATABASE IF NOT EXISTS xunrana_blog DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE xunrana_blog;

-- -----------------------------------------------------------
-- Table: user
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `username`   VARCHAR(50)  NOT NULL,
    `password`   VARCHAR(255) NOT NULL,
    `nickname`   VARCHAR(50)  NULL,
    `avatar`     VARCHAR(255) NULL,
    `email`      VARCHAR(100) NULL,
    `role`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0=user, 1=admin',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '0=disabled, 1=normal',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User table';

-- -----------------------------------------------------------
-- Table: category
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `category` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(50) NOT NULL,
    `slug`        VARCHAR(50) NOT NULL,
    `description` VARCHAR(255) NULL,
    `sort_order`  INT         NOT NULL DEFAULT 0,
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Category table';

-- -----------------------------------------------------------
-- Table: tag
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tag` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `name`       VARCHAR(50) NOT NULL,
    `slug`       VARCHAR(50) NOT NULL,
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tag table';

-- -----------------------------------------------------------
-- Table: article
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `article` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `title`         VARCHAR(200)  NOT NULL,
    `slug`          VARCHAR(200)  NOT NULL,
    `summary`       VARCHAR(500)  NULL,
    `content`       MEDIUMTEXT    NOT NULL,
    `cover_image`   VARCHAR(255)  NULL,
    `category_id`   BIGINT        NULL,
    `author_id`     BIGINT        NOT NULL,
    `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '0=draft, 1=published',
    `is_top`        TINYINT       NOT NULL DEFAULT 0,
    `view_count`    INT           NOT NULL DEFAULT 0,
    `like_count`    INT           NOT NULL DEFAULT 0,
    `comment_count` INT           NOT NULL DEFAULT 0,
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `published_at`  DATETIME      NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slug` (`slug`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_author_id` (`author_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `fk_article_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_article_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Article table';

-- -----------------------------------------------------------
-- Table: article_tag (composite PK)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `article_tag` (
    `article_id` BIGINT NOT NULL,
    `tag_id`     BIGINT NOT NULL,
    PRIMARY KEY (`article_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`),
    CONSTRAINT `fk_article_tag_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_article_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Article-Tag relation table';

-- -----------------------------------------------------------
-- Table: comment
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `comment` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `article_id` BIGINT       NOT NULL,
    `parent_id`  BIGINT       NULL,
    `nickname`   VARCHAR(50)  NOT NULL,
    `email`      VARCHAR(100) NULL,
    `content`    TEXT         NOT NULL,
    `status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=pending, 1=approved, 2=rejected',
    `ip`         VARCHAR(45)  NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_article_id` (`article_id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_comment_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Comment table';

-- -----------------------------------------------------------
-- Table: operation_log
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NULL,
    `module`     VARCHAR(50)  NULL,
    `operation`  VARCHAR(50)  NULL,
    `method`     VARCHAR(255) NULL,
    `params`     TEXT         NULL,
    `ip`         VARCHAR(45)  NULL,
    `duration`   BIGINT       NULL COMMENT 'Duration in milliseconds',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Operation log table';

-- -----------------------------------------------------------
-- Seed data: default admin user
-- Password: admin123 (BCrypt encoded)
-- -----------------------------------------------------------
INSERT INTO `user` (`username`, `password`, `nickname`, `role`, `status`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Administrator', 1, 1);
