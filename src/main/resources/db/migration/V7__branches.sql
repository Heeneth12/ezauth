SET search_path TO auth;

CREATE TABLE branches
(
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    branch_name   VARCHAR(255) NOT NULL,
    branch_code   VARCHAR(100) NOT NULL,
    is_head_office BOOLEAN     NOT NULL DEFAULT false,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_branch_code_tenant UNIQUE (tenant_id, branch_code),
    CONSTRAINT fk_branch_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);

ALTER TABLE users
    ADD COLUMN branch_id BIGINT,
    ADD CONSTRAINT fk_user_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE SET NULL;

ALTER TABLE user_roles
    ADD COLUMN branch_id BIGINT,
    ADD CONSTRAINT fk_user_role_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE SET NULL;

CREATE INDEX idx_branches_tenant_id ON branches (tenant_id);
CREATE INDEX idx_branches_is_active ON branches (is_active);
CREATE INDEX idx_users_branch_id ON users (branch_id);
CREATE INDEX idx_user_roles_branch_id ON user_roles (branch_id);
