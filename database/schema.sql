-- Smart Duplicate File Finder & Storage Cleaner - MySQL 8 schema.
-- Safe to run repeatedly. The application also runs this file at startup.
-- All event times are epoch milliseconds (BIGINT) to avoid timezone surprises.

CREATE DATABASE IF NOT EXISTS smart_duplicate_finder CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_duplicate_finder;

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          VARCHAR(16)  NOT NULL DEFAULT 'ADMIN',
  email         VARCHAR(190) NULL,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_users_username (username),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS settings (
  skey   VARCHAR(64) NOT NULL PRIMARY KEY,
  svalue TEXT        NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scans (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  folder            VARCHAR(1024) NOT NULL,
  status            VARCHAR(16)   NOT NULL,
  started_at        BIGINT        NOT NULL,
  ended_at          BIGINT        NULL,
  total_files       BIGINT        NOT NULL DEFAULT 0,
  total_folders     BIGINT        NOT NULL DEFAULT 0,
  total_bytes       BIGINT        NOT NULL DEFAULT 0,
  duplicate_files   BIGINT        NOT NULL DEFAULT 0,
  duplicate_groups  BIGINT        NOT NULL DEFAULT 0,
  recoverable_bytes BIGINT        NOT NULL DEFAULT 0,
  skipped_files     BIGINT        NOT NULL DEFAULT 0,
  username          VARCHAR(64)   NOT NULL,
  message           VARCHAR(255)  NULL,
  INDEX idx_scans_status (status),
  INDEX idx_scans_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS files (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  path         VARCHAR(1024) NOT NULL,
  path_hash    CHAR(64)      NOT NULL,
  dir          VARCHAR(1024) NOT NULL,
  name         VARCHAR(512)  NOT NULL,
  extension    VARCHAR(32)   NOT NULL DEFAULT '',
  size         BIGINT        NOT NULL,
  hash         VARCHAR(128)  NULL,
  mime         VARCHAR(100)  NULL,
  created_ms   BIGINT        NULL,
  modified_ms  BIGINT        NULL,
  accessed_ms  BIGINT        NULL,
  status       VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
  last_scan_id BIGINT        NULL,
  UNIQUE KEY uq_files_path_hash (path_hash),
  INDEX idx_files_hash (hash),
  INDEX idx_files_size (size),
  INDEX idx_files_path (path(255)),
  INDEX idx_files_status (status),
  INDEX idx_files_last_scan (last_scan_id),
  CONSTRAINT fk_files_scan FOREIGN KEY (last_scan_id) REFERENCES scans(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS duplicate_groups (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  scan_id           BIGINT       NOT NULL,
  hash              VARCHAR(128) NOT NULL,
  size              BIGINT       NOT NULL,
  file_count        INT          NOT NULL,
  recoverable_bytes BIGINT       NOT NULL,
  INDEX idx_dg_hash (hash),
  INDEX idx_dg_scan (scan_id),
  CONSTRAINT fk_dg_scan FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scan_files (
  scan_id  BIGINT NOT NULL,
  file_id  BIGINT NOT NULL,
  group_id BIGINT NULL,
  PRIMARY KEY (scan_id, file_id),
  INDEX idx_sf_scan (scan_id),
  INDEX idx_sf_group (group_id),
  CONSTRAINT fk_sf_scan  FOREIGN KEY (scan_id)  REFERENCES scans(id)            ON DELETE CASCADE,
  CONSTRAINT fk_sf_file  FOREIGN KEY (file_id)  REFERENCES files(id)            ON DELETE CASCADE,
  CONSTRAINT fk_sf_group FOREIGN KEY (group_id) REFERENCES duplicate_groups(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quarantine (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  file_id         BIGINT        NULL,
  original_path   VARCHAR(1024) NOT NULL,
  quarantine_path VARCHAR(1024) NOT NULL,
  name            VARCHAR(512)  NOT NULL,
  size            BIGINT        NOT NULL,
  hash            VARCHAR(128)  NULL,
  quarantined_at  BIGINT        NOT NULL,
  resolved_at     BIGINT        NULL,
  username        VARCHAR(64)   NOT NULL,
  status          VARCHAR(16)   NOT NULL DEFAULT 'QUARANTINED',
  INDEX idx_q_status (status),
  INDEX idx_q_file (file_id),
  INDEX idx_q_hash (hash),
  CONSTRAINT fk_q_file FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS activity_logs (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  ts          BIGINT        NOT NULL,
  username    VARCHAR(64)   NOT NULL,
  action      VARCHAR(32)   NOT NULL,
  description VARCHAR(1000) NOT NULL,
  ip          VARCHAR(64)   NULL,
  INDEX idx_al_ts (ts),
  INDEX idx_al_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
