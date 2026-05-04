CREATE TABLE user_requests
(
    id            BIGSERIAL PRIMARY KEY,
    user_req_uuid UUID         NOT NULL UNIQUE,

    tenant_uuid   UUID,
    user_uuid     UUID,
    assigned_uuid UUID,

    contact_email VARCHAR(255) NOT NULL,
    contact_name  VARCHAR(255),

    subject       VARCHAR(255) NOT NULL,
    description   TEXT         NOT NULL,

    source_url    TEXT         NOT NULL,
    source_name   VARCHAR(100) NOT NULL,

    category      VARCHAR(50)  NOT NULL,
    status        VARCHAR(50)  NOT NULL,
    priority      VARCHAR(50)  NOT NULL,

    metadata      JSONB DEFAULT '{}'::jsonb,

    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_user_requests_tenant_status
    ON user_requests (tenant_uuid, status);

CREATE INDEX idx_user_requests_tenant_category
    ON user_requests (tenant_uuid, category);

CREATE INDEX idx_user_requests_email
    ON user_requests (contact_email);