-- V1__Create_RBAC_Schema.sql
-- Flyway Migration for Role-Based Access Control System

-- Drop all tables in correct order
DROP TABLE IF EXISTS user_module_privileges CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS user_applications CASCADE;
DROP TABLE IF EXISTS tenant_applications CASCADE;
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

-- Add comment
COMMENT ON TABLE tenants IS 'Multi-tenant isolation - each tenant represents an organization';
COMMENT ON COLUMN tenants.tenant_uuid IS 'Unique identifier for external references';
COMMENT ON COLUMN tenants.is_personal IS 'Flag to identify personal vs organizational tenants';

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
COMMENT ON COLUMN applications.app_key IS 'Unique application identifier key';

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

COMMENT ON TABLE modules IS 'Modules within each application';
COMMENT ON COLUMN modules.module_key IS 'Unique key for module within application scope';

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

COMMENT ON TABLE privileges IS 'Granular permissions within each module';
COMMENT ON COLUMN privileges.privilege_key IS 'Action key like VIEW, CREATE, UPDATE, DELETE';

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
COMMENT ON COLUMN users.user_uuid IS 'External UUID for API responses';

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

COMMENT ON TABLE roles IS 'Tenant-specific roles for grouping privileges';
COMMENT ON COLUMN roles.is_system_role IS 'Flag for predefined system roles';

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

COMMENT ON TABLE user_applications IS 'Maps which applications a user has access to';

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

COMMENT ON TABLE user_roles IS 'Assigns roles to users with optional expiration';
COMMENT ON COLUMN user_roles.expires_at IS 'Optional expiration timestamp for temporary role assignments';

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

COMMENT ON TABLE user_module_privileges IS 'Direct privilege assignments to users (overrides role privileges)';

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

COMMENT ON TABLE tenant_applications IS 'Defines which applications are available to each tenant';


CREATE TABLE flyway_test (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50)
);



-- =========================
-- 11. ADD CIRCULAR FOREIGN KEY
-- =========================
ALTER TABLE tenants
ADD CONSTRAINT fk_tenant_admin FOREIGN KEY (tenant_admin_user_id)
    REFERENCES users(id) ON DELETE SET NULL;

COMMENT ON COLUMN tenants.tenant_admin_user_id IS 'Primary administrator user for the tenant';

-- =========================
-- 12. CREATE INDEXES
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
CREATE INDEX idx_user_roles_is_active ON user_roles(is_active);
CREATE INDEX idx_user_roles_expires_at ON user_roles(expires_at) WHERE expires_at IS NOT NULL;

-- User Module Privileges indexes
CREATE INDEX idx_user_module_privileges_user_app_id ON user_module_privileges(user_application_id);
CREATE INDEX idx_user_module_privileges_module_id ON user_module_privileges(module_id);
CREATE INDEX idx_user_module_privileges_privilege_id ON user_module_privileges(privilege_id);
CREATE INDEX idx_user_module_privileges_is_active ON user_module_privileges(is_active);

-- Tenant Applications indexes
CREATE INDEX idx_tenant_applications_tenant_id ON tenant_applications(tenant_id);
CREATE INDEX idx_tenant_applications_application_id ON tenant_applications(application_id);