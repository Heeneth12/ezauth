CREATE TABLE integrations (
    id                BIGSERIAL PRIMARY KEY,
    integration_uuid  VARCHAR(36)  NOT NULL UNIQUE,
    tenant_id         BIGINT       NOT NULL,
    integration_type  VARCHAR(100) NOT NULL,
    display_name      VARCHAR(255),
    primary_key       TEXT,
    secondary_key     TEXT,
    tertiary_key      TEXT,
    is_test_mode      BOOLEAN      NOT NULL DEFAULT false,
    is_connected      BOOLEAN      NOT NULL DEFAULT false,
    is_active         BOOLEAN      NOT NULL DEFAULT true,
    webhook_config    TEXT,
    extra_config      TEXT,
    links             TEXT,
    connected_at      TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_integration_type UNIQUE (tenant_id, integration_type),
    CONSTRAINT fk_integration_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE INDEX idx_integrations_tenant_id ON integrations(tenant_id);
CREATE INDEX idx_integrations_type     ON integrations(integration_type);
