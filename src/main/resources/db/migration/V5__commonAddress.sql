CREATE TABLE addresses
(
    id            BIGSERIAL PRIMARY KEY,
    entity_type   VARCHAR(20) NOT NULL, --'TENANT', 'USER'
    entity_id     BIGINT      NOT NULL,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    route         VARCHAR(100),
    area          VARCHAR(100),
    city          VARCHAR(100),
    state         VARCHAR(100),
    country       VARCHAR(100),
    pin_code      VARCHAR(20),
    address_type  VARCHAR(50) NOT NULL, -- BILLING, SHIPPING, etc.
    is_primary    BOOLEAN     NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Only one primary per entity+address_type
CREATE UNIQUE INDEX uk_primary_address
    ON addresses (entity_type, entity_id, address_type) WHERE is_primary = TRUE;
CREATE INDEX idx_addresses_entity ON addresses (entity_type, entity_id);