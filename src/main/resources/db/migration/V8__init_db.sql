CREATE SCHEMA IF NOT EXISTS auth;
SET
search_path TO auth;
-- =============================================================
-- DROP ORDER: children → parents
-- =============================================================
DROP TABLE IF EXISTS auth.user_module_privileges CASCADE;
DROP TABLE IF EXISTS auth.user_roles CASCADE;
DROP TABLE IF EXISTS auth.user_applications CASCADE;
DROP TABLE IF EXISTS auth.role_privileges CASCADE;
DROP TABLE IF EXISTS auth.tenant_applications CASCADE;
DROP TABLE IF EXISTS auth.addresses CASCADE;
DROP TABLE IF EXISTS auth.users CASCADE;
DROP TABLE IF EXISTS auth.roles CASCADE;
DROP TABLE IF EXISTS auth.branches CASCADE;
DROP TABLE IF EXISTS auth.privileges CASCADE;
DROP TABLE IF EXISTS auth.modules CASCADE;
DROP TABLE IF EXISTS auth.applications CASCADE;
DROP TABLE IF EXISTS auth.subscriptions CASCADE;
DROP TABLE IF EXISTS auth.subscription_plans CASCADE;
DROP TABLE IF EXISTS auth.tenant_details CASCADE;
DROP TABLE IF EXISTS auth.tenants CASCADE;


-- =============================================================
-- 1. SUBSCRIPTION PLANS
-- =============================================================
CREATE TABLE auth.subscription_plans
(
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100)   NOT NULL UNIQUE,
    description   TEXT,
    type          VARCHAR(20)    NOT NULL CHECK (type IN ('LIFETIME', 'MONTHLY', 'YEARLY')),
    price         NUMERIC(19, 2) NOT NULL DEFAULT 0.00 CHECK (price >= 0),
    duration_days INTEGER        NOT NULL CHECK (duration_days > 0),
    max_users     INTEGER CHECK (max_users IS NULL OR max_users > 0),
    is_active     BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);


-- =============================================================
-- 2. TENANTS
-- =============================================================
CREATE TABLE auth.tenants
(
    id                   BIGSERIAL PRIMARY KEY,
    tenant_uuid          VARCHAR(36)  NOT NULL UNIQUE,
    tenant_name          VARCHAR(255) NOT NULL,
    tenant_code          VARCHAR(100) NOT NULL UNIQUE,
    is_personal          BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    is_verify            BOOLEAN      NOT NULL DEFAULT FALSE,
    tenant_admin_user_id BIGINT, -- FK added below
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);


-- =============================================================
-- 3. TENANT DETAILS (1:1)
-- =============================================================
CREATE TABLE auth.tenant_details
(
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL UNIQUE
        REFERENCES auth.tenants (id) ON DELETE CASCADE,
    business_type VARCHAR(50)  NOT NULL,
    legal_name    VARCHAR(255) NOT NULL,
    base_currency CHAR(3)      NOT NULL,
    time_zone     VARCHAR(100) NOT NULL DEFAULT 'UTC',
    gst_number    VARCHAR(50) UNIQUE,
    cin_number    VARCHAR(50) UNIQUE,
    is_gst_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    pan_number    VARCHAR(20),
    trade_name    VARCHAR(255),
    kyc_status    VARCHAR(50),
    incorporation_date TIMESTAMPTZ,
    support_email VARCHAR(255),
    contact_phone VARCHAR(50),
    logo_url      TEXT,
    website       TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- =============================================================
-- 4. SUBSCRIPTIONS
-- =============================================================
CREATE TABLE auth.subscriptions
(
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT      NOT NULL REFERENCES auth.tenants (id) ON DELETE CASCADE,
    plan_id    BIGINT      NOT NULL REFERENCES auth.subscription_plans (id),
    status     VARCHAR(20) NOT NULL CHECK (status IN ('TRIAL', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    start_date TIMESTAMPTZ NOT NULL,
    is_primary BOOLEAN     NOT NULL DEFAULT FALSE,
    end_date   TIMESTAMPTZ NOT NULL,
    auto_renew BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_sub_dates CHECK (end_date > start_date)
);

-- Only one ACTIVE or TRIAL subscription per tenant at a time
CREATE UNIQUE INDEX uk_one_active_sub_per_tenant
    ON auth.subscriptions (tenant_id) WHERE status IN ('ACTIVE', 'TRIAL');


-- =============================================================
-- 5. BRANCHES
-- =============================================================
CREATE TABLE auth.branches
(
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL REFERENCES auth.tenants (id) ON DELETE CASCADE,
    branch_name    VARCHAR(255) NOT NULL,
    branch_code    VARCHAR(100) NOT NULL,
    is_head_office BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_branch_code UNIQUE (tenant_id, branch_code)
);

-- Only one head office per tenant
CREATE UNIQUE INDEX uk_one_head_office_per_tenant
    ON auth.branches (tenant_id) WHERE is_head_office = TRUE;


-- =============================================================
-- 6. APPLICATIONS  (platform-level — not tenant-scoped)
-- =============================================================
CREATE TABLE auth.applications
(
    id          BIGSERIAL PRIMARY KEY,
    app_name    VARCHAR(255) NOT NULL,
    app_key     VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);


-- =============================================================
-- 7. MODULES  (belongs to application)
-- =============================================================
CREATE TABLE auth.modules
(
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT       NOT NULL REFERENCES auth.applications (id) ON DELETE CASCADE,
    module_name    VARCHAR(255) NOT NULL,
    module_key     VARCHAR(100) NOT NULL,
    description    TEXT,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_app_module_key UNIQUE (application_id, module_key)
);


-- =============================================================
-- 8. PRIVILEGES  (belongs to module)
-- =============================================================
CREATE TABLE auth.privileges
(
    id             BIGSERIAL PRIMARY KEY,
    module_id      BIGINT       NOT NULL REFERENCES auth.modules (id) ON DELETE CASCADE,
    privilege_name VARCHAR(255) NOT NULL,
    privilege_key  VARCHAR(100) NOT NULL,
    description    TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_module_privilege_key UNIQUE (module_id, privilege_key)
);


-- =============================================================
-- 9. USERS
-- branch_id  NULL  → TENANT_ADMIN (no branch scope)
-- branch_id  SET   → branch-scoped user
-- DB enforces: TENANT_ADMIN cannot have a branch
-- =============================================================
CREATE TABLE auth.users
(
    id                   BIGSERIAL PRIMARY KEY,
    user_uuid            VARCHAR(50)  NOT NULL UNIQUE,
    tenant_id            BIGINT       NOT NULL REFERENCES auth.tenants (id) ON DELETE CASCADE,
    branch_id            BIGINT REFERENCES auth.branches (id) ON DELETE RESTRICT,
    full_name            VARCHAR(255) NOT NULL,
    email                VARCHAR(255) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    phone                VARCHAR(50),
    profile_picture_uuid VARCHAR(100),
    account_scope        VARCHAR(50)  NOT NULL,
    user_type            VARCHAR(50)  NOT NULL,
    is_login_enabled     BOOLEAN      NOT NULL        DEFAULT TRUE,
    is_active            BOOLEAN      NOT NULL        DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL        DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL        DEFAULT NOW(),
);

-- Circular FK: tenants → users
ALTER TABLE auth.tenants
    ADD CONSTRAINT fk_tenant_admin_user
        FOREIGN KEY (tenant_admin_user_id) REFERENCES auth.users (id) ON DELETE SET NULL;


-- =============================================================
-- 10. ROLES  (tenant-level templates — not enforced at runtime)
-- =============================================================
CREATE TABLE auth.roles
(
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL REFERENCES auth.tenants (id) ON DELETE CASCADE,
    role_name      VARCHAR(255) NOT NULL,
    role_key       VARCHAR(100) NOT NULL,
    description    TEXT,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_system_role BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_tenant_role_name UNIQUE (tenant_id, role_name),
    CONSTRAINT uk_tenant_role_key UNIQUE (tenant_id, role_key)
);


-- =============================================================
-- 11. ROLE PRIVILEGES  (what each role template contains)
-- =============================================================
CREATE TABLE auth.role_privileges
(
    id           BIGSERIAL PRIMARY KEY,
    role_id      BIGINT      NOT NULL REFERENCES auth.roles (id) ON DELETE CASCADE,
    privilege_id BIGINT      NOT NULL REFERENCES auth.privileges (id) ON DELETE CASCADE,
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_role_privilege UNIQUE (role_id, privilege_id)
);


-- =============================================================
-- 12. USER ROLES  (template assignments — audit trail only)
-- =============================================================
CREATE TABLE auth.user_roles
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    role_id     BIGINT      NOT NULL REFERENCES auth.roles (id) ON DELETE CASCADE,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by BIGINT,
    expires_at  TIMESTAMPTZ,

    CONSTRAINT uk_user_role UNIQUE (user_id, role_id),
    CONSTRAINT chk_role_expiry CHECK (expires_at IS NULL OR expires_at > assigned_at)
);


-- =============================================================
-- 13. USER APPLICATIONS  (which apps a user can access)
-- Gated by tenant_applications — user can only be assigned
-- apps the tenant has subscribed to (enforce in service layer)
-- =============================================================
CREATE TABLE auth.user_applications
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    application_id BIGINT      NOT NULL REFERENCES auth.applications (id) ON DELETE CASCADE,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    assigned_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_user_application UNIQUE (user_id, application_id)
);


-- =============================================================
-- 14. USER MODULE PRIVILEGES  (exact privilege list per user per app)
-- module_id removed — always derive via privilege.module_id
-- =============================================================
CREATE TABLE auth.user_module_privileges
(
    id                  BIGSERIAL PRIMARY KEY,
    user_application_id BIGINT  NOT NULL REFERENCES auth.user_applications (id) ON DELETE CASCADE,
    privilege_id        BIGINT  NOT NULL REFERENCES auth.privileges (id) ON DELETE CASCADE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT uk_user_app_privilege UNIQUE (user_application_id, privilege_id)
);


-- =============================================================
-- 15. TENANT APPLICATIONS  (which apps tenant is subscribed to)
-- =============================================================
CREATE TABLE auth.tenant_applications
(
    tenant_id      BIGINT      NOT NULL REFERENCES auth.tenants (id) ON DELETE CASCADE,
    application_id BIGINT      NOT NULL REFERENCES auth.applications (id) ON DELETE CASCADE,
    enabled_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tenant_app PRIMARY KEY (tenant_id, application_id)
);


-- =============================================================
-- 16. ADDRESSES  (unified polymorphic)
-- entity_type : TENANT | USER | BRANCH
-- entity_id   : id of the owning row
-- =============================================================
CREATE TABLE auth.addresses
(
    id            BIGSERIAL PRIMARY KEY,
    entity_type   VARCHAR(10) NOT NULL CHECK (entity_type IN ('TENANT', 'USER', 'BRANCH')),
    entity_id     BIGINT      NOT NULL,
    address_type  VARCHAR(20) NOT NULL CHECK (address_type IN ('BILLING', 'SHIPPING', 'REGISTERED', 'OPERATIONAL')),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    route         VARCHAR(100),
    area          VARCHAR(100),
    city          VARCHAR(100),
    state         VARCHAR(100),
    country       VARCHAR(100),
    pin_code      VARCHAR(20),
    is_primary    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One primary address per entity per address_type
CREATE UNIQUE INDEX uk_primary_address
    ON auth.addresses (entity_type, entity_id, address_type) WHERE is_primary = TRUE;


-- =============================================================
-- 17. INDEXES
-- =============================================================

-- tenants
CREATE INDEX idx_tenants_is_active ON auth.tenants (is_active);

-- branches
CREATE INDEX idx_branches_tenant_id ON auth.branches (tenant_id);
CREATE INDEX idx_branches_tenant_active ON auth.branches (tenant_id, is_active);

-- users
CREATE INDEX idx_users_tenant_id ON auth.users (tenant_id);
CREATE INDEX idx_users_branch_id ON auth.users (branch_id);
CREATE INDEX idx_users_email ON auth.users (email);
CREATE INDEX idx_users_tenant_type_active ON auth.users (tenant_id, user_type, is_active);

-- roles
CREATE INDEX idx_roles_tenant_id ON auth.roles (tenant_id);
CREATE INDEX idx_roles_tenant_active ON auth.roles (tenant_id, is_active);

-- role_privileges
CREATE INDEX idx_rp_role_id ON auth.role_privileges (role_id);
CREATE INDEX idx_rp_privilege_id ON auth.role_privileges (privilege_id);

-- user_roles
CREATE INDEX idx_ur_user_id ON auth.user_roles (user_id);
CREATE INDEX idx_ur_role_id ON auth.user_roles (role_id);
CREATE INDEX idx_ur_user_active ON auth.user_roles (user_id, is_active);

-- user_applications
CREATE INDEX idx_ua_user_id ON auth.user_applications (user_id);
CREATE INDEX idx_ua_application_id ON auth.user_applications (application_id);
CREATE INDEX idx_ua_user_active ON auth.user_applications (user_id, is_active);

-- user_module_privileges
CREATE INDEX idx_ump_user_application_id ON auth.user_module_privileges (user_application_id);
CREATE INDEX idx_ump_privilege_id ON auth.user_module_privileges (privilege_id);

-- tenant_applications
CREATE INDEX idx_ta_tenant_id ON auth.tenant_applications (tenant_id);

-- subscriptions
CREATE INDEX idx_sub_tenant_status ON auth.subscriptions (tenant_id, status);
CREATE INDEX idx_sub_plan_id ON auth.subscriptions (plan_id);

-- modules
CREATE INDEX idx_modules_app_id ON auth.modules (application_id);
CREATE INDEX idx_modules_active ON auth.modules (application_id, is_active);

-- privileges
CREATE INDEX idx_privileges_module_id ON auth.privileges (module_id);

-- addresses
CREATE INDEX idx_addresses_entity ON auth.addresses (entity_type, entity_id);


-- =============================================================
-- 19. SEED — SUBSCRIPTION PLANS
-- =============================================================
INSERT INTO auth.subscription_plans (name, description, type, price, duration_days, max_users)
VALUES ('Free Trial', 'Full platform access for 14 days.', 'LIFETIME', 0.00, 14, 2),
       ('Basic', 'Essential tools for small teams.', 'MONTHLY', 9.99, 30, 5),
       ('Pro Monthly', 'Advanced features for growing businesses.', 'MONTHLY', 29.99, 30, 20),
       ('Pro Yearly', 'Best value for established teams. Save 17%.', 'YEARLY', 299.00, 365, 20),
       ('Enterprise', 'Unlimited access with priority support.', 'YEARLY', 999.00, 365, NULL);


-- =============================================================
-- 20. SEED — APPLICATION
-- =============================================================
INSERT INTO auth.applications (id, app_name, app_key, description)
VALUES (1, 'Inventory Management', 'EZH_INV_APP', 'Core system for stock, sales, and purchase tracking');


-- =============================================================
-- 21. SEED — MODULES
-- =============================================================
INSERT INTO auth.modules (id, module_name, module_key, application_id)
VALUES (1, 'Dashboard', 'EZH_INV_DASHBOARD', 1),
       (2, 'Items Management', 'EZH_INV_ITEMS', 1),
       (3, 'Stock Control', 'EZH_INV_STOCK', 1),
       (4, 'Purchases', 'EZH_INV_PURCHASES', 1),
       (5, 'Sales', 'EZH_INV_SALES', 1),
       (6, 'Contacts', 'EZH_INV_CONTACTS', 1),
       (7, 'Employees', 'EZH_INV_EMPLOYEE', 1),
       (8, 'Reports', 'EZH_INV_REPORTS', 1),
       (9, 'Documents', 'EZH_INV_DOCUMENTS', 1),
       (10, 'Admin', 'EZH_INV_USER_MGMT', 1),
       (11, 'Settings', 'EZH_INV_SETTINGS', 1),
       (12, 'Vendor Management', 'EZH_INV_VENDOR', 1);


-- =============================================================
-- 22. SEED — PRIVILEGES
-- =============================================================
INSERT INTO auth.privileges (privilege_name, privilege_key, module_id)
VALUES
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

    -- EMPLOYEES
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
    ('Update Settings', 'EZH_INV_SETTINGS_EDIT', 11),

    -- VENDOR
    ('View Vendors', 'EZH_INV_VENDOR_VIEW', 12),
    ('Create Vendors', 'EZH_INV_VENDOR_CREATE', 12),
    ('Edit Vendors', 'EZH_INV_VENDOR_EDIT', 12),
    ('Delete Vendors', 'EZH_INV_VENDOR_DELETE', 12);