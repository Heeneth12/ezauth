-- V1__Create_RBAC_Schema.sql
-- Flyway Migration for Role-Based Access Control System

-- Drop all tables in correct order
DROP TABLE IF EXISTS user_module_privileges CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS user_applications CASCADE;
DROP TABLE IF EXISTS tenant_applications CASCADE;
DROP TABLE IF EXISTS user_address CASCADE;   -- ADDED
DROP TABLE IF EXISTS tenant_address CASCADE; -- ADDED
DROP TABLE IF EXISTS privileges CASCADE;
DROP TABLE IF EXISTS modules CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS applications CASCADE;
DROP TABLE IF EXISTS tenants CASCADE;
DROP TABLE IF EXISTS flyway_test CASCADE;

-- =========================
-- 1. TENANTS TABLE
-- =========================
CREATE TABLE tenants (
    id BIGSERIAL PRIMARY KEY,
    tenant_uuid VARCHAR(36) NOT NULL UNIQUE,
    tenant_name VARCHAR(255) NOT NULL,
    tenant_code VARCHAR(100) NOT NULL UNIQUE,
    is_personal BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    tenant_admin_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE tenants IS 'Multi-tenant isolation - each tenant represents an organization';
COMMENT ON COLUMN tenants.tenant_uuid IS 'Unique identifier for external references';

-- =========================
-- 2. APPLICATIONS TABLE
-- =========================
CREATE TABLE applications (
    id BIGSERIAL PRIMARY KEY,
    app_name VARCHAR(255) NOT NULL,
    app_key VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE applications IS 'Defines available applications in the system';

-- =========================
-- 3. MODULES TABLE
-- =========================
CREATE TABLE modules (
    id BIGSERIAL PRIMARY KEY,
    module_name VARCHAR(255) NOT NULL,
    module_key VARCHAR(100) NOT NULL,
    description TEXT,
    application_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_module UNIQUE (application_id, module_key),
    CONSTRAINT fk_module_application FOREIGN KEY (application_id)
        REFERENCES applications(id) ON DELETE CASCADE
);

-- =========================
-- 4. PRIVILEGES TABLE
-- =========================
CREATE TABLE privileges (
    id BIGSERIAL PRIMARY KEY,
    privilege_name VARCHAR(255) NOT NULL,
    privilege_key VARCHAR(100) NOT NULL,
    description TEXT,
    module_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_module_privilege UNIQUE (module_id, privilege_key),
    CONSTRAINT fk_privilege_module FOREIGN KEY (module_id)
        REFERENCES modules(id) ON DELETE CASCADE
);

-- =========================
-- 5. USERS TABLE
-- =========================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    user_uuid VARCHAR(36) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT true,
    tenant_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE CASCADE
);

COMMENT ON TABLE users IS 'System users belonging to tenants';

-- =========================
-- 6. ROLES TABLE
-- =========================
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(255) NOT NULL,
    role_key VARCHAR(100) NOT NULL,
    description TEXT,
    tenant_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_system_role BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_role UNIQUE (tenant_id, role_name),
    CONSTRAINT fk_role_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE CASCADE
);

-- =========================
-- 7. USER_APPLICATIONS TABLE
-- =========================
CREATE TABLE user_applications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_application UNIQUE (user_id, application_id),
    CONSTRAINT fk_ua_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ua_application FOREIGN KEY (application_id)
        REFERENCES applications(id) ON DELETE CASCADE
);

-- =========================
-- 8. USER_ROLES TABLE
-- =========================
CREATE TABLE user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id)
        REFERENCES roles(id) ON DELETE CASCADE
);

-- =========================
-- 9. USER_MODULE_PRIVILEGES TABLE
-- =========================
CREATE TABLE user_module_privileges (
    id BIGSERIAL PRIMARY KEY,
    user_application_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    privilege_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uk_user_module_privilege UNIQUE (user_application_id, module_id, privilege_id),
    CONSTRAINT fk_ump_user_app FOREIGN KEY (user_application_id)
        REFERENCES user_applications(id) ON DELETE CASCADE,
    CONSTRAINT fk_ump_module FOREIGN KEY (module_id)
        REFERENCES modules(id) ON DELETE CASCADE,
    CONSTRAINT fk_ump_privilege FOREIGN KEY (privilege_id)
        REFERENCES privileges(id) ON DELETE CASCADE
);

-- =========================
-- 10. TENANT_APPLICATIONS TABLE
-- =========================
CREATE TABLE tenant_applications (
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    CONSTRAINT pk_tenant_applications PRIMARY KEY (tenant_id, application_id),
    CONSTRAINT fk_ta_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_ta_application FOREIGN KEY (application_id)
        REFERENCES applications(id) ON DELETE CASCADE
);

-- =========================
-- 11. TENANT ADDRESS TABLE (ADDED)
-- =========================
CREATE TABLE tenant_address (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    route VARCHAR(100),
    area VARCHAR(100),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    pin_code VARCHAR(20),
    address_type VARCHAR(50) NOT NULL, -- Enum: HEAD_OFFICE, BILLING, etc.
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_addr_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE CASCADE
);

COMMENT ON TABLE tenant_address IS 'Stores official addresses for the tenant organization';

-- =========================
-- 12. USER ADDRESS TABLE (ADDED)
-- =========================
CREATE TABLE user_address (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL, -- Kept for data partitioning
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    route VARCHAR(100),
    area VARCHAR(100),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    pin_code VARCHAR(20),
    address_type VARCHAR(50) NOT NULL, -- Enum: HOME, SHIPPING, etc.
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_addr_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_addr_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE CASCADE
);

COMMENT ON TABLE user_address IS 'Stores personal addresses for individual users';


CREATE TABLE flyway_test (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50)
);

-- =========================
-- 13. ADD CIRCULAR FOREIGN KEY
-- =========================
ALTER TABLE tenants
ADD CONSTRAINT fk_tenant_admin FOREIGN KEY (tenant_admin_user_id)
    REFERENCES users(id) ON DELETE SET NULL;

-- =========================
-- 14. CREATE INDEXES
-- =========================

-- Users indexes
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_is_active ON users(is_active);

-- Modules indexes
CREATE INDEX idx_modules_application_id ON modules(application_id);
CREATE INDEX idx_modules_is_active ON modules(is_active);

-- Privileges indexes
CREATE INDEX idx_privileges_module_id ON privileges(module_id);

-- Roles indexes
CREATE INDEX idx_roles_tenant_id ON roles(tenant_id);
CREATE INDEX idx_roles_is_active ON roles(is_active);

-- User Applications indexes
CREATE INDEX idx_user_applications_user_id ON user_applications(user_id);
CREATE INDEX idx_user_applications_application_id ON user_applications(application_id);
CREATE INDEX idx_user_applications_is_active ON user_applications(is_active);

-- User Roles indexes
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- User Module Privileges indexes
CREATE INDEX idx_user_module_privileges_ua_id ON user_module_privileges(user_application_id);

-- Tenant Applications indexes
CREATE INDEX idx_tenant_applications_tenant_id ON tenant_applications(tenant_id);

-- Tenant Address indexes (ADDED)
CREATE INDEX idx_tenant_address_tenant_id ON tenant_address(tenant_id);

-- User Address indexes (ADDED)
CREATE INDEX idx_user_address_user_id ON user_address(user_id);
CREATE INDEX idx_user_address_tenant_id ON user_address(tenant_id);