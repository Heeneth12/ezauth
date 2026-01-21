-- V2__Add_Subscription_Module.sql
-- Adds Subscription Plans, Subscriptions, and updates Tenants table

-- ==========================================
-- 1. CREATE SUBSCRIPTION PLANS TABLE
-- ==========================================
CREATE TABLE subscription_plans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    type VARCHAR(50) NOT NULL,
    price NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    duration_days INTEGER NOT NULL, -- 30, 365, etc.
    max_users INTEGER, -- Limit on number of users allowed
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE subscription_plans IS 'Catalog of available subscription tiers';

-- ==========================================
-- 2. SEED DEFAULT PLANS
-- ==========================================
INSERT INTO subscription_plans
(name, description, type, price, duration_days, max_users, is_active)
VALUES
('Free Trial', 'Experience the full platform for 14 days.', 'LIFETIME', 0.00, 14, 2, true),
('Basic', 'Essential tools for small teams.', 'MONTHLY', 9.99, 30, 5, true),
('Pro Monthly', 'Advanced features for growing businesses.', 'MONTHLY', 29.99, 30, 20, true),
('Pro Yearly', 'Best value for established teams. Save 17%.', 'YEARLY', 299.00, 365, 20, true),
('Enterprise', 'Unlimited access and priority support for large organizations.', 'YEARLY', 999.00, 365, 1000, true);

-- ==========================================
-- 3. CREATE SUBSCRIPTIONS TABLE
-- ==========================================
CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL, -- Enum: ACTIVE, EXPIRED, CANCELLED
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    auto_renew BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_plan FOREIGN KEY (plan_id)
        REFERENCES subscription_plans(id),
    CONSTRAINT fk_sub_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE CASCADE
);

COMMENT ON TABLE subscriptions IS 'Active and historical subscriptions for tenants';

-- ==========================================
-- 4. UPDATE TENANTS TABLE (Add new column)
-- ==========================================
-- We use ALTER TABLE because 'tenants' already exists from V1
ALTER TABLE tenants
ADD COLUMN current_subscription_id BIGINT;

-- Add the Foreign Key constraint linking Tenant -> Subscription
ALTER TABLE tenants
ADD CONSTRAINT fk_tenant_curr_sub FOREIGN KEY (current_subscription_id)
    REFERENCES subscriptions(id) ON DELETE SET NULL;

-- ==========================================
-- 5. CREATE INDEXES FOR PERFORMANCE
-- ==========================================

-- Subscriptions Indexes
CREATE INDEX idx_subscriptions_tenant_id ON subscriptions(tenant_id);
CREATE INDEX idx_subscriptions_plan_id ON subscriptions(plan_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);

-- Tenant Index for the new column
CREATE INDEX idx_tenants_current_subscription ON tenants(current_subscription_id);