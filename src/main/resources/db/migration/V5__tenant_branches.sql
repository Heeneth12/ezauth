SET search_path TO auth;

CREATE TABLE IF NOT EXISTS tenant_branches (
    id BIGSERIAL PRIMARY KEY,
    branch_uuid VARCHAR(36) NOT NULL UNIQUE,
    branch_name VARCHAR(255) NOT NULL,
    branch_code VARCHAR(100) NOT NULL,
    description TEXT,
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    route VARCHAR(100),
    area VARCHAR(100),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    pin_code VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT true,
    tenant_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_branch_code UNIQUE (tenant_id, branch_code),
    CONSTRAINT uk_tenant_branch_name UNIQUE (tenant_id, branch_name),
    CONSTRAINT fk_branch_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS branch_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = 'auth'
          AND table_name = 'users'
          AND constraint_name = 'fk_user_branch'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT fk_user_branch
            FOREIGN KEY (branch_id) REFERENCES tenant_branches(id) ON DELETE SET NULL;
    END IF;
END $$;

INSERT INTO tenant_branches (
    branch_uuid,
    branch_name,
    branch_code,
    tenant_id,
    is_active,
    created_at,
    updated_at
)
SELECT
    md5(random()::text || clock_timestamp()::text),
    'Main Branch',
    t.tenant_code || '-MAIN',
    t.id,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1
    FROM tenant_branches tb
    WHERE tb.tenant_id = t.id
      AND (tb.branch_code = t.tenant_code || '-MAIN' OR LOWER(tb.branch_name) = 'main branch')
);

UPDATE users u
SET branch_id = tb.id
FROM tenant_branches tb
WHERE tb.tenant_id = u.tenant_id
  AND tb.branch_code = (SELECT t.tenant_code || '-MAIN' FROM tenants t WHERE t.id = u.tenant_id)
  AND u.branch_id IS NULL;
