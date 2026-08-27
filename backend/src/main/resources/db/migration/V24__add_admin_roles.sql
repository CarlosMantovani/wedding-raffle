ALTER TABLE admin_user
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'MASTER';

ALTER TABLE admin_user
    ADD CONSTRAINT chk_admin_user_role
        CHECK (role IN ('MASTER', 'CASHIER'));

INSERT INTO admin_user (username, password_hash, role)
VALUES ('cash', '$2a$10$LnbcgkDoz97XktSdOBDJVevPIEd3S/0W1pt119jytgpkrl0u5kOcC', 'CASHIER')
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role;
