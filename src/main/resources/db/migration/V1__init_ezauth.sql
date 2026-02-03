-- ==========================================================
-- 1. DROP EXISTING TABLES (CLEAN SLATE)
-- ==========================================================
DROP TABLE IF EXISTS user_module_privileges CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS user_applications CASCADE;
DROP TABLE IF EXISTS tenant_applications CASCADE;
DROP TABLE IF EXISTS user_address CASCADE;
DROP TABLE IF EXISTS tenant_address CASCADE;
DROP TABLE IF EXISTS privileges CASCADE;
DROP TABLE IF EXISTS modules CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS subscriptions CASCADE;
DROP TABLE IF EXISTS subscription_plans CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS applications CASCADE;
DROP TABLE IF EXISTS tenants CASCADE;
DROP TABLE IF EXISTS flyway_test CASCADE;

-- ==========================================================
-- 2. CORE TABLES (TENANTS & APPLICATIONS)
-- ==========================================================
CREATE TABLE tenants (
    id BIGSERIAL PRIMARY KEY,
    tenant_uuid VARCHAR(36) NOT NULL UNIQUE,
    tenant_name VARCHAR(255) NOT NULL,
    tenant_code VARCHAR(100) NOT NULL UNIQUE,
    is_personal BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    tenant_admin_user_id BIGINT,
    current_subscription_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE applications (
    id BIGSERIAL PRIMARY KEY,
    app_name VARCHAR(255) NOT NULL,
    app_key VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================================
-- 3. APP STRUCTURE (MODULES & PRIVILEGES)
-- ==========================================================
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
    CONSTRAINT fk_module_application FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE
);

CREATE TABLE privileges (
    id BIGSERIAL PRIMARY KEY,
    privilege_name VARCHAR(255) NOT NULL,
    privilege_key VARCHAR(100) NOT NULL,
    description TEXT,
    module_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_module_privilege UNIQUE (module_id, privilege_key),
    CONSTRAINT fk_privilege_module FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE
);

-- ==========================================================
-- 4. USERS & ROLES
-- ==========================================================
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
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

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
    CONSTRAINT fk_role_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- ==========================================================
-- 5. MAPPING TABLES (ASSIGNMENTS)
-- ==========================================================
CREATE TABLE user_applications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_application UNIQUE (user_id, application_id),
    CONSTRAINT fk_ua_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ua_application FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE
);

CREATE TABLE user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE user_module_privileges (
    id BIGSERIAL PRIMARY KEY,
    user_application_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    privilege_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uk_user_module_privilege UNIQUE (user_application_id, module_id, privilege_id),
    CONSTRAINT fk_ump_user_app FOREIGN KEY (user_application_id) REFERENCES user_applications(id) ON DELETE CASCADE,
    CONSTRAINT fk_ump_module FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE,
    CONSTRAINT fk_ump_privilege FOREIGN KEY (privilege_id) REFERENCES privileges(id) ON DELETE CASCADE
);

CREATE TABLE tenant_applications (
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    CONSTRAINT pk_tenant_applications PRIMARY KEY (tenant_id, application_id),
    CONSTRAINT fk_ta_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_ta_application FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE
);

-- ==========================================================
-- 6. SUBSCRIPTIONS
-- ==========================================================
CREATE TABLE subscription_plans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    type VARCHAR(50) NOT NULL,
    price NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    duration_days INTEGER NOT NULL,
    max_users INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    auto_renew BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id),
    CONSTRAINT fk_sub_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- ==========================================================
-- 7. ADDRESS TABLES
-- ==========================================================
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
    address_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_addr_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE TABLE user_address (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    route VARCHAR(100),
    area VARCHAR(100),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    pin_code VARCHAR(20),
    address_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_addr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_addr_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- ==========================================================
-- 8. UTILITY & LATE CONSTRAINTS
-- ==========================================================
CREATE TABLE flyway_test (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50)
);

-- Circular references resolved after tables exist
ALTER TABLE tenants ADD CONSTRAINT fk_tenant_admin FOREIGN KEY (tenant_admin_user_id) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE tenants ADD CONSTRAINT fk_tenant_curr_sub FOREIGN KEY (current_subscription_id) REFERENCES subscriptions(id) ON DELETE SET NULL;


-- ==========================================================
-- SEED APPLICATIONS
-- ==========================================================

-- 1. INSERT APPLICATION
INSERT INTO applications (id, app_name, app_key, description)
VALUES (1, 'Inventory Management', 'EZH_INV_APP', 'Core system for stock, sales, and purchase tracking');

-- 2. INSERT MODULES
-- We explicitly set IDs to ensure they match the privilege inserts below
INSERT INTO modules (id, module_name, module_key, application_id) VALUES
(1, 'Dashboard', 'EZH_INV_DASHBOARD', 1),
(2, 'Items Management', 'EZH_INV_ITEMS', 1),
(3, 'Stock Control', 'EZH_INV_STOCK', 1),
(4, 'Purchases', 'EZH_INV_PURCHASES', 1),
(5, 'Sales', 'EZH_INV_SALES', 1),
(6, 'Contacts', 'EZH_INV_CONTACTS', 1),
(7, 'Employees', 'EZH_INV_EMPLOYEE', 1),
(8, 'Reports', 'EZH_INV_REPORTS', 1),
(9, 'Documents', 'EZH_INV_DOCUMENTS', 1),
(10, 'Admin', 'EZH_INV_USER_MGMT', 1),
(11, 'Settings', 'EZH_INV_SETTINGS', 1);

-- 3. INSERT PRIVILEGES
INSERT INTO privileges (privilege_name, privilege_key, module_id) VALUES
-- DASHBOARD
('View Dashboard', 'EZH_INV_DASHBOARD_VIEW', 1),

-- ITEMS
('View Items', 'EZH_INV_ITEMS_VIEW', 2),
('Create Items', 'EZH_INV_ITEMS_CREATE', 2),
('Edit Items', 'EZH_INV_ITEMS_EDIT', 2),
('Delete Items', 'EZH_INV_ITEMS_DELETE', 2),
('Export Items', 'EZH_INV_ITEMS_EXPORT', 2),

-- STOCK
('View Stock', 'EZH_INV_STOCK_VIEW', 3),
('Adjust Stock', 'EZH_INV_STOCK_EDIT', 3),
('Export Stock', 'EZH_INV_STOCK_EXPORT', 3),

-- PURCHASES
('View Purchases', 'EZH_INV_PURCHASES_VIEW', 4),
('Create Purchases', 'EZH_INV_PURCHASES_CREATE', 4),
('Edit Purchases', 'EZH_INV_PURCHASES_EDIT', 4),
('Delete Purchases', 'EZH_INV_PURCHASES_DELETE', 4),
('Approve Purchases', 'EZH_INV_PURCHASES_APPROVE', 4),

-- SALES
('View Sales', 'EZH_INV_SALES_VIEW', 5),
('Create Sales', 'EZH_INV_SALES_CREATE', 5),
('Edit Sales', 'EZH_INV_SALES_EDIT', 5),
('Delete Sales', 'EZH_INV_SALES_DELETE', 5),
('Approve Sales', 'EZH_INV_SALES_APPROVE', 5),

-- CONTACTS
('View Contacts', 'EZH_INV_CONTACTS_VIEW', 6),
('Create Contacts', 'EZH_INV_CONTACTS_CREATE', 6),
('Edit Contacts', 'EZH_INV_CONTACTS_EDIT', 6),
('Delete Contacts', 'EZH_INV_CONTACTS_DELETE', 6),

-- EMPLOYEES / APPROVAL
('View Employees', 'EZH_INV_EMPLOYEE_VIEW', 7),
('Create Employees', 'EZH_INV_EMPLOYEE_CREATE', 7),
('Edit Employees', 'EZH_INV_EMPLOYEE_EDIT', 7),
('Approve Requests', 'EZH_INV_EMPLOYEE_APPROVE', 7),

-- REPORTS
('View Reports', 'EZH_INV_REPORTS_VIEW', 8),
('Export Reports', 'EZH_INV_REPORTS_EXPORT', 8),

-- DOCUMENTS
('View Documents', 'EZH_INV_DOCUMENTS_VIEW', 9),
('Upload Documents', 'EZH_INV_DOCUMENTS_CREATE', 9),
('Delete Documents', 'EZH_INV_DOCUMENTS_DELETE', 9),

-- ADMIN / USER MGMT
('View Users', 'EZH_INV_USER_MGMT_VIEW', 10),
('Manage Roles', 'EZH_INV_USER_MGMT_EDIT', 10),

-- SETTINGS
('View Settings', 'EZH_INV_SETTINGS_VIEW', 11),
('Update Settings', 'EZH_INV_SETTINGS_EDIT', 11);


-- ==========================================================
-- 9. SEED DEFAULT PLANS
-- ==========================================================
INSERT INTO subscription_plans (name, description, type, price, duration_days, max_users, is_active)
VALUES
('Free Trial', 'Experience the full platform for 14 days.', 'LIFETIME', 0.00, 14, 2, true),
('Basic', 'Essential tools for small teams.', 'MONTHLY', 9.99, 30, 5, true),
('Pro Monthly', 'Advanced features for growing businesses.', 'MONTHLY', 29.99, 30, 20, true),
('Pro Yearly', 'Best value for established teams. Save 17%.', 'YEARLY', 299.00, 365, 20, true),
('Enterprise', 'Unlimited access and priority support for large organizations.', 'YEARLY', 999.00, 365, 1000, true);

-- ==========================================================
-- 10. INDEXES
-- ==========================================================
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_is_active ON users(is_active);
CREATE INDEX idx_modules_application_id ON modules(application_id);
CREATE INDEX idx_modules_is_active ON modules(is_active);
CREATE INDEX idx_privileges_module_id ON privileges(module_id);
CREATE INDEX idx_roles_tenant_id ON roles(tenant_id);
CREATE INDEX idx_roles_is_active ON roles(is_active);
CREATE INDEX idx_user_applications_user_id ON user_applications(user_id);
CREATE INDEX idx_user_applications_application_id ON user_applications(application_id);
CREATE INDEX idx_user_applications_is_active ON user_applications(is_active);
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_user_module_privileges_ua_id ON user_module_privileges(user_application_id);
CREATE INDEX idx_tenant_applications_tenant_id ON tenant_applications(tenant_id);
CREATE INDEX idx_tenant_address_tenant_id ON tenant_address(tenant_id);
CREATE INDEX idx_user_address_user_id ON user_address(user_id);
CREATE INDEX idx_user_address_tenant_id ON user_address(tenant_id);
CREATE INDEX idx_subscriptions_tenant_id ON subscriptions(tenant_id);
CREATE INDEX idx_subscriptions_plan_id ON subscriptions(plan_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
CREATE INDEX idx_tenants_current_subscription ON tenants(current_subscription_id);