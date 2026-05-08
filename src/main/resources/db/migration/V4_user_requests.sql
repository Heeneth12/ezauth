CREATE TABLE auth.user_requests
(
    id            bigserial    NOT NULL,
    user_req_uuid varchar(50)  NOT NULL,
    tenant_uuid   varchar(50) NULL,
    user_uuid     varchar(50) NULL,
    assigned_uuid varchar(50) NULL,
    contact_email varchar(255) NOT NULL,
    contact_name  varchar(255) NULL,
    subject       varchar(255) NOT NULL,
    description   text         NOT NULL,
    source_url    text         NOT NULL,
    source_name   varchar(100) NOT NULL,
    category      varchar(50)  NOT NULL,
    status        varchar(50)  NOT NULL,
    priority      varchar(50)  NOT NULL,
    metadata      jsonb       DEFAULT '{}'::jsonb NULL,
    created_at    timestamptz DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at    timestamptz DEFAULT CURRENT_TIMESTAMP NULL,
    resolved_at   timestamptz NULL,
    CONSTRAINT user_requests_pkey PRIMARY KEY (id),
    CONSTRAINT user_requests_user_req_uuid_key UNIQUE (user_req_uuid)
);
CREATE INDEX idx_user_requests_email ON auth.user_requests USING btree (contact_email);
CREATE INDEX idx_user_requests_tenant_category ON auth.user_requests USING btree (tenant_uuid, category);
CREATE INDEX idx_user_requests_tenant_status ON auth.user_requests USING btree (tenant_uuid, status);