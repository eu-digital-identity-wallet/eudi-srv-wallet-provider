-- v1
CREATE TABLE IF NOT EXISTS challenges (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, `value` VARBINARY(128) NOT NULL, created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL, active BOOLEAN NOT NULL);
ALTER TABLE challenges ADD CONSTRAINT challenges_value_unique_idx UNIQUE (`value`);
CREATE INDEX challenges_idx_1 ON challenges (created_at, expires_at, active);