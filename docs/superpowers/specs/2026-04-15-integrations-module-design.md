# Integrations Module Design

**Date:** 2026-04-15
**Status:** Approved
**Project:** ezauth — Multi-Tenant SaaS Auth Platform

---

## Overview

The Integrations module allows each tenant to connect third-party services to the platform. Supported categories include payment gateways (Razorpay, Stripe), communication (WhatsApp, Slack), CRM (ZOHO), and email (SendGrid, SMTP). Each tenant can hold one integration per `IntegrationType`. Sensitive credentials are encrypted at rest using AES. Webhooks and extra config are stored as JSON blobs for flexibility.

---

## Architecture

### Package Structure

```
com.ezh.ezauth.integrations/
├── entity/
│   ├── Integration.java
│   └── IntegrationType.java
├── dto/
│   ├── IntegrationRequest.java
│   ├── IntegrationDto.java
│   └── TestConnectionResponse.java
├── repository/
│   └── IntegrationRepository.java
├── service/
│   └── IntegrationService.java
├── controller/
│   └── IntegrationController.java
└── converter/
    └── EncryptedStringConverter.java
```

Also includes: `src/main/resources/db/migration/V4__integrations.sql`

### Layer Responsibilities

- **Controller** — validates input, delegates to service, returns `ResponseResource<T>`
- **Service** — business logic, entity mapping, masks credentials in responses
- **Repository** — JPA queries (find by tenant, find by tenant+type)
- **Converter** — transparent AES encrypt/decrypt on key fields via `@Convert`
- **Entity** — JPA entity with `@PrePersist` UUID generation

---

## Data Model

### `integrations` Table

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGSERIAL | PK | |
| `integration_uuid` | VARCHAR(36) | NOT NULL | Auto-generated on persist |
| `tenant_id` | BIGINT | FK → tenants, NOT NULL | |
| `integration_type` | VARCHAR(100) | NOT NULL | Enum value |
| `display_name` | VARCHAR(255) | | Human-readable label |
| `primary_key` | TEXT | | AES-encrypted |
| `secondary_key` | TEXT | | AES-encrypted |
| `tertiary_key` | TEXT | | AES-encrypted |
| `is_test_mode` | BOOLEAN | NOT NULL DEFAULT false | Sandbox flag |
| `is_connected` | BOOLEAN | NOT NULL DEFAULT false | Last known connection status |
| `is_active` | BOOLEAN | NOT NULL DEFAULT true | Soft enable/disable |
| `webhook_config` | TEXT | | JSON blob: URLs, events, secrets |
| `extra_config` | TEXT | | JSON blob: provider-specific fields |
| `links` | TEXT | | JSON blob: dashboard/docs links |
| `connected_at` | TIMESTAMP | | When last successfully connected |
| `created_at` | TIMESTAMP | NOT NULL | Hibernate `@CreationTimestamp` |
| `updated_at` | TIMESTAMP | NOT NULL | Hibernate `@UpdateTimestamp` |

**Unique constraint:** `(tenant_id, integration_type)` — one integration per type per tenant.

### `IntegrationType` Enum Values

`RAZORPAY`, `RAZORPAY_TEST`, `STRIPE`, `STRIPE_TEST`, `WHATSAPP`, `ZOHO`, `SENDGRID`, `EMAIL_SMTP`, `SLACK`, `WEBHOOK_GENERIC`

---

## API Design

Base path: `/api/v1/tenant/{tenantId}/integrations`

| Method | Path | Description | Response |
|---|---|---|---|
| `POST` | `/` | Create integration | `CommonResponse` (id + message) |
| `GET` | `/` | List all for tenant | `List<IntegrationDto>` |
| `GET` | `/{integrationId}` | Get by ID | `IntegrationDto` |
| `PUT` | `/{integrationId}` | Update credentials/config | `CommonResponse` |
| `DELETE` | `/{integrationId}` | Soft-delete (isActive=false) | `CommonResponse` |
| `PATCH` | `/{integrationId}/toggle` | Toggle isActive | `CommonResponse` |
| `POST` | `/{integrationId}/test-connection` | Validate credentials | `TestConnectionResponse` |

### Key Behaviours

- **Credential masking:** `GET` responses always return `"****"` for `primaryKey`, `secondaryKey`, `tertiaryKey` — raw values are never exposed via API.
- **Duplicate guard:** Creating two integrations of the same type for the same tenant throws `409 CONFLICT`.
- **test-connection:** Updates `isConnected` and `connectedAt` on the entity. Returns `{ connected, message, testedAt }`. The initial implementation performs a structural validation (checks required keys are present and non-empty); actual provider pinging is a future enhancement noted in the response message.
- **Soft delete:** `DELETE` sets `isActive = false`. Integration record is retained for audit.
- **Toggle:** `PATCH /toggle` flips `isActive` and returns the new state.

---

## Encryption

`EncryptedStringConverter` implements `AttributeConverter<String, String>`:
- Reads `app.encryption.secret-key` (32-char AES-256 key) from `application.properties`
- `convertToDatabaseColumn` — AES/CBC/PKCS5 encrypt → Base64
- `convertToEntityAttribute` — Base64 decode → AES decrypt
- Null-safe: returns null if input is null
- Applied via `@Convert(converter = EncryptedStringConverter.class)` on `primaryKey`, `secondaryKey`, `tertiaryKey`

---

## DTOs

### `IntegrationRequest` (create & update)
```
integrationTyp, displayName, primaryKey, secondaryKey, tertiaryKey,
isTestMode, webhookConfig (String JSON), extraConfig (String JSON), links (String JSON)
```

### `IntegrationDto` (response — credentials masked)
```
id, integrationUuid, tenantId, integrationType, displayName,
primaryKey ("****"), secondaryKey ("****"), tertiaryKey ("****"),
isTestMode, isConnected, isActive, webhookConfig, extraConfig, links,
connectedAt, createdAt, updatedAt
```

### `TestConnectionResponse`
```
connected (boolean), message (String), testedAt (LocalDateTime)
```

---

## Error Handling

- Tenant not found → `404 NOT_FOUND` via `CommonException`
- Integration not found → `404 NOT_FOUND`
- Duplicate type for tenant → `409 CONFLICT`
- Missing required keys for test-connection → `400 BAD_REQUEST`

---

## Database Migration

New file: `V4__integrations.sql`
- Creates `integrations` table
- Adds unique index on `(tenant_id, integration_type)`
- Adds FK to `tenants`
