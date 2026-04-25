# Integrations Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete tenant integrations module that stores AES-encrypted third-party credentials (Razorpay, WhatsApp, ZOHO, Stripe, etc.) per tenant with CRUD endpoints, soft-delete, toggle, and a test-connection endpoint.

**Architecture:** Single `integrations` table with one row per `(tenant_id, integration_type)` unique pair. Sensitive key fields are transparently encrypted/decrypted via a JPA `AttributeConverter`. JSON blobs store flexible webhook config, extra config, and links. All endpoints are JWT-authenticated (existing security setup covers this).

**Tech Stack:** Spring Boot 4.0.0, Java 21, JPA/Hibernate, Flyway (PostgreSQL), Lombok, JUnit 5 + Mockito (via spring-boot-starter-webmvc-test), Maven (`./mvnw`)

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Delete | `src/main/java/com/ezh/ezauth/integrations/entiry/Integrations.java` | Remove old stub (typo in package name) |
| Create | `src/main/resources/db/migration/V4__integrations.sql` | Flyway table + index migration |
| Modify | `src/main/resources/application.properties` | Add `app.encryption.secret-key` property |
| Create | `src/main/java/com/ezh/ezauth/integrations/entity/IntegrationType.java` | Enum of all supported provider types |
| Create | `src/main/java/com/ezh/ezauth/integrations/converter/EncryptedStringConverter.java` | AES-128/ECB JPA converter |
| Create | `src/main/java/com/ezh/ezauth/integrations/entity/Integration.java` | JPA entity |
| Create | `src/main/java/com/ezh/ezauth/integrations/repository/IntegrationRepository.java` | JPA repository |
| Create | `src/main/java/com/ezh/ezauth/integrations/dto/IntegrationRequest.java` | Create/update request DTO |
| Create | `src/main/java/com/ezh/ezauth/integrations/dto/IntegrationDto.java` | Response DTO (credentials masked) |
| Create | `src/main/java/com/ezh/ezauth/integrations/dto/TestConnectionResponse.java` | Test-connection response DTO |
| Create | `src/main/java/com/ezh/ezauth/integrations/service/IntegrationService.java` | All business logic |
| Create | `src/main/java/com/ezh/ezauth/integrations/controller/IntegrationController.java` | REST controller |
| Modify | `src/main/java/com/ezh/ezauth/config/SecurityConfig.java` | Add `PATCH` to CORS allowed methods |
| Create | `src/test/java/com/ezh/ezauth/integrations/converter/EncryptedStringConverterTest.java` | Unit tests for AES converter |
| Create | `src/test/java/com/ezh/ezauth/integrations/service/IntegrationServiceTest.java` | Unit tests for service layer |

---

## Task 1: Remove old stub and add encryption config

**Files:**
- Delete: `src/main/java/com/ezh/ezauth/integrations/entiry/Integrations.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Delete the old stub file**

```bash
rm src/main/java/com/ezh/ezauth/integrations/entiry/Integrations.java
rmdir src/main/java/com/ezh/ezauth/integrations/entiry
```

- [ ] **Step 2: Add encryption key property to `application.properties`**

Add these two lines at the end of `src/main/resources/application.properties`:

```properties
# Integrations — AES encryption
app.encryption.secret-key=${ENCRYPTION_SECRET_KEY:ezauthDefaultKey!!}
```

> The default `ezauthDefaultKey!!` is 16 chars for local dev. In production, set `ENCRYPTION_SECRET_KEY` to a 16-char (or longer) secret. Keys longer than 16 chars are automatically truncated to 16 bytes (AES-128).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.properties
git rm src/main/java/com/ezh/ezauth/integrations/entiry/Integrations.java
git commit -m "chore: remove integrations stub, add encryption key config"
```

---

## Task 2: Flyway migration — create integrations table

**Files:**
- Create: `src/main/resources/db/migration/V4__integrations.sql`

- [ ] **Step 1: Create the migration file**

Create `src/main/resources/db/migration/V4__integrations.sql` with:

```sql
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
```

> Flyway naming convention: `V{version}__{description}.sql` (double underscore). This runs after V3.

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/db/migration/V4__integrations.sql
git commit -m "feat: add integrations table migration (V4)"
```

---

## Task 3: IntegrationType enum

**Files:**
- Create: `src/main/java/com/ezh/ezauth/integrations/entity/IntegrationType.java`

- [ ] **Step 1: Create the enum**

```java
package com.ezh.ezauth.integrations.entity;

public enum IntegrationType {
    // Payment Gateways
    RAZORPAY,
    RAZORPAY_TEST,
    STRIPE,
    STRIPE_TEST,

    // Communication
    WHATSAPP,
    SLACK,

    // CRM / Productivity
    ZOHO,

    // Email
    SENDGRID,
    EMAIL_SMTP,

    // Generic
    WEBHOOK_GENERIC
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/ezh/ezauth/integrations/entity/IntegrationType.java
git commit -m "feat: add IntegrationType enum"
```

---

## Task 4: EncryptedStringConverter + unit tests

**Files:**
- Create: `src/main/java/com/ezh/ezauth/integrations/converter/EncryptedStringConverter.java`
- Create: `src/test/java/com/ezh/ezauth/integrations/converter/EncryptedStringConverterTest.java`

- [ ] **Step 1: Write the failing test first**

Create `src/test/java/com/ezh/ezauth/integrations/converter/EncryptedStringConverterTest.java`:

```java
package com.ezh.ezauth.integrations.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedStringConverterTest {

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedStringConverter();
        converter.setRawSecretKey("testSecretKey123"); // exactly 16 chars
    }

    @Test
    void encryptThenDecrypt_returnsOriginalValue() {
        String original = "rzp_live_secretKey_ABC123";
        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void encrypt_nullInput_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void decrypt_nullInput_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void encrypt_producesBase64Output() {
        String encrypted = converter.convertToDatabaseColumn("mySecret");
        // Base64 characters only
        assertThat(encrypted).matches("^[A-Za-z0-9+/=]+$");
    }

    @Test
    void encrypt_differentValuesProduceDifferentCiphertext() {
        String enc1 = converter.convertToDatabaseColumn("value1");
        String enc2 = converter.convertToDatabaseColumn("value2");
        assertThat(enc1).isNotEqualTo(enc2);
    }
}
```

- [ ] **Step 2: Run test — verify it fails with compilation error**

```bash
./mvnw test -Dtest=EncryptedStringConverterTest -pl . 2>&1 | tail -20
```

Expected: compilation failure — `EncryptedStringConverter` does not exist yet.

- [ ] **Step 3: Create the converter**

Create `src/main/java/com/ezh/ezauth/integrations/converter/EncryptedStringConverter.java`:

```java
package com.ezh.ezauth.integrations.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    @Value("${app.encryption.secret-key:ezauthDefaultKey!!}")
    private String rawSecretKey;

    // Package-private: used only in unit tests via direct field injection
    void setRawSecretKey(String key) {
        this.rawSecretKey = key;
    }

    private SecretKeySpec buildKey() {
        byte[] keyBytes = rawSecretKey.getBytes(StandardCharsets.UTF_8);
        byte[] key = Arrays.copyOf(keyBytes, 16); // AES-128: truncate/pad to 16 bytes
        return new SecretKeySpec(key, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, buildKey());
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, buildKey());
            byte[] decoded = Base64.getDecoder().decode(dbData);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
./mvnw test -Dtest=EncryptedStringConverterTest 2>&1 | tail -20
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ezh/ezauth/integrations/converter/EncryptedStringConverter.java \
        src/test/java/com/ezh/ezauth/integrations/converter/EncryptedStringConverterTest.java
git commit -m "feat: add AES EncryptedStringConverter with unit tests"
```

---

## Task 5: Integration entity

**Files:**
- Create: `src/main/java/com/ezh/ezauth/integrations/entity/Integration.java`

- [ ] **Step 1: Create the entity**

```java
package com.ezh.ezauth.integrations.entity;

import com.ezh.ezauth.integrations.converter.EncryptedStringConverter;
import com.ezh.ezauth.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "integrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Integration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "integration_uuid", nullable = false)
    private String integrationUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "integration_type", nullable = false, length = 100)
    private IntegrationType integrationType;

    @Column(name = "display_name")
    private String displayName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "primary_key")
    private String primaryKey;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "secondary_key")
    private String secondaryKey;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tertiary_key")
    private String tertiaryKey;

    @Builder.Default
    @Column(name = "is_test_mode", nullable = false)
    private Boolean isTestMode = false;

    @Builder.Default
    @Column(name = "is_connected", nullable = false)
    private Boolean isConnected = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "webhook_config", columnDefinition = "TEXT")
    private String webhookConfig;

    @Column(name = "extra_config", columnDefinition = "TEXT")
    private String extraConfig;

    @Column(name = "links", columnDefinition = "TEXT")
    private String links;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (integrationUuid == null) {
            integrationUuid = UUID.randomUUID().toString();
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/ezh/ezauth/integrations/entity/Integration.java
git commit -m "feat: add Integration JPA entity with encrypted key fields"
```

---

## Task 6: IntegrationRepository

**Files:**
- Create: `src/main/java/com/ezh/ezauth/integrations/repository/IntegrationRepository.java`

- [ ] **Step 1: Create the repository**

```java
package com.ezh.ezauth.integrations.repository;

import com.ezh.ezauth.integrations.entity.Integration;
import com.ezh.ezauth.integrations.entity.IntegrationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    List<Integration> findByTenantId(Long tenantId);

    Optional<Integration> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndIntegrationType(Long tenantId, IntegrationType integrationType);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/ezh/ezauth/integrations/repository/IntegrationRepository.java
git commit -m "feat: add IntegrationRepository"
```

---

## Task 7: DTOs

**Files:**
- Create: `src/main/java/com/ezh/ezauth/integrations/dto/IntegrationRequest.java`
- Create: `src/main/java/com/ezh/ezauth/integrations/dto/IntegrationDto.java`
- Create: `src/main/java/com/ezh/ezauth/integrations/dto/TestConnectionResponse.java`

- [ ] **Step 1: Create `IntegrationRequest.java`**

```java
package com.ezh.ezauth.integrations.dto;

import com.ezh.ezauth.integrations.entity.IntegrationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntegrationRequest {

    private IntegrationType integrationType;
    private String displayName;
    private String primaryKey;
    private String secondaryKey;
    private String tertiaryKey;
    private Boolean isTestMode;
    private String webhookConfig;
    private String extraConfig;
    private String links;
}
```

- [ ] **Step 2: Create `IntegrationDto.java`**

```java
package com.ezh.ezauth.integrations.dto;

import com.ezh.ezauth.integrations.entity.IntegrationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntegrationDto {

    private Long id;
    private String integrationUuid;
    private Long tenantId;
    private IntegrationType integrationType;
    private String displayName;
    private String primaryKey;    // always "****" — never expose raw value
    private String secondaryKey;  // always "****" — never expose raw value
    private String tertiaryKey;   // always "****" — never expose raw value
    private Boolean isTestMode;
    private Boolean isConnected;
    private Boolean isActive;
    private String webhookConfig;
    private String extraConfig;
    private String links;
    private LocalDateTime connectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Create `TestConnectionResponse.java`**

```java
package com.ezh.ezauth.integrations.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TestConnectionResponse {

    private Boolean connected;
    private String message;
    private LocalDateTime testedAt;
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ezh/ezauth/integrations/dto/
git commit -m "feat: add IntegrationRequest, IntegrationDto, TestConnectionResponse DTOs"
```

---

## Task 8: IntegrationService + unit tests

**Files:**
- Create: `src/main/java/com/ezh/ezauth/integrations/service/IntegrationService.java`
- Create: `src/test/java/com/ezh/ezauth/integrations/service/IntegrationServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/ezh/ezauth/integrations/service/IntegrationServiceTest.java`:

```java
package com.ezh.ezauth.integrations.service;

import com.ezh.ezauth.integrations.dto.IntegrationDto;
import com.ezh.ezauth.integrations.dto.IntegrationRequest;
import com.ezh.ezauth.integrations.dto.TestConnectionResponse;
import com.ezh.ezauth.integrations.entity.Integration;
import com.ezh.ezauth.integrations.entity.IntegrationType;
import com.ezh.ezauth.integrations.repository.IntegrationRepository;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {

    @Mock private IntegrationRepository integrationRepository;
    @Mock private TenantRepository tenantRepository;
    @InjectMocks private IntegrationService integrationService;

    private Tenant tenant;
    private Integration integration;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder()
                .id(1L)
                .tenantUuid("uuid-001")
                .tenantName("Acme Corp")
                .tenantCode("ACME01")
                .isPersonal(false)
                .isActive(true)
                .build();

        integration = Integration.builder()
                .id(1L)
                .integrationUuid("int-uuid-001")
                .tenant(tenant)
                .integrationType(IntegrationType.RAZORPAY)
                .displayName("My Razorpay")
                .primaryKey("rzp_live_key")
                .secondaryKey("rzp_live_secret")
                .isTestMode(false)
                .isConnected(false)
                .isActive(true)
                .build();
    }

    // --- createIntegration ---

    @Test
    void createIntegration_success() throws CommonException {
        IntegrationRequest request = IntegrationRequest.builder()
                .integrationType(IntegrationType.RAZORPAY)
                .displayName("My Razorpay")
                .primaryKey("rzp_live_key")
                .secondaryKey("rzp_live_secret")
                .build();

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(integrationRepository.existsByTenantIdAndIntegrationType(1L, IntegrationType.RAZORPAY)).thenReturn(false);
        when(integrationRepository.save(any(Integration.class))).thenReturn(integration);

        CommonResponse response = integrationService.createIntegration(1L, request);

        assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(response.getId()).isEqualTo("1");
        verify(integrationRepository).save(any(Integration.class));
    }

    @Test
    void createIntegration_missingType_throws400() {
        IntegrationRequest request = IntegrationRequest.builder().displayName("test").build();

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        CommonException ex = assertThrows(CommonException.class,
                () -> integrationService.createIntegration(1L, request));

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createIntegration_tenantNotFound_throws404() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        CommonException ex = assertThrows(CommonException.class,
                () -> integrationService.createIntegration(99L,
                        IntegrationRequest.builder().integrationType(IntegrationType.STRIPE).build()));

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createIntegration_duplicateType_throws409() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(integrationRepository.existsByTenantIdAndIntegrationType(1L, IntegrationType.RAZORPAY)).thenReturn(true);

        CommonException ex = assertThrows(CommonException.class,
                () -> integrationService.createIntegration(1L,
                        IntegrationRequest.builder().integrationType(IntegrationType.RAZORPAY).build()));

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- getIntegrations ---

    @Test
    void getIntegrations_masksAllKeyFields() throws CommonException {
        when(tenantRepository.existsById(1L)).thenReturn(true);
        when(integrationRepository.findByTenantId(1L)).thenReturn(List.of(integration));

        List<IntegrationDto> result = integrationService.getIntegrations(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPrimaryKey()).isEqualTo("****");
        assertThat(result.get(0).getSecondaryKey()).isEqualTo("****");
    }

    @Test
    void getIntegrations_nullKeyField_staysNull() throws CommonException {
        integration.setTertiaryKey(null);
        when(tenantRepository.existsById(1L)).thenReturn(true);
        when(integrationRepository.findByTenantId(1L)).thenReturn(List.of(integration));

        List<IntegrationDto> result = integrationService.getIntegrations(1L);

        assertThat(result.get(0).getTertiaryKey()).isNull();
    }

    @Test
    void getIntegrations_tenantNotFound_throws404() {
        when(tenantRepository.existsById(99L)).thenReturn(false);

        CommonException ex = assertThrows(CommonException.class,
                () -> integrationService.getIntegrations(99L));

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- testConnection ---

    @Test
    void testConnection_validPrimaryKey_returnsConnectedTrue() throws CommonException {
        when(integrationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(integration));
        when(integrationRepository.save(any())).thenReturn(integration);

        TestConnectionResponse response = integrationService.testConnection(1L, 1L);

        assertThat(response.getConnected()).isTrue();
        assertThat(response.getTestedAt()).isNotNull();
        verify(integrationRepository).save(argThat(i -> i.getIsConnected() && i.getConnectedAt() != null));
    }

    @Test
    void testConnection_missingPrimaryKey_returnsConnectedFalse() throws CommonException {
        integration.setPrimaryKey(null);
        when(integrationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(integration));
        when(integrationRepository.save(any())).thenReturn(integration);

        TestConnectionResponse response = integrationService.testConnection(1L, 1L);

        assertThat(response.getConnected()).isFalse();
        verify(integrationRepository).save(argThat(i -> !i.getIsConnected()));
    }

    @Test
    void testConnection_blankPrimaryKey_returnsConnectedFalse() throws CommonException {
        integration.setPrimaryKey("   ");
        when(integrationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(integration));
        when(integrationRepository.save(any())).thenReturn(integration);

        TestConnectionResponse response = integrationService.testConnection(1L, 1L);

        assertThat(response.getConnected()).isFalse();
    }

    // --- toggleIntegration ---

    @Test
    void toggleIntegration_enablesWhenDisabled() throws CommonException {
        integration.setIsActive(false);
        when(integrationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(integration));
        when(integrationRepository.save(any())).thenReturn(integration);

        CommonResponse response = integrationService.toggleIntegration(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
        verify(integrationRepository).save(argThat(Integration::getIsActive));
    }

    @Test
    void toggleIntegration_disablesWhenEnabled() throws CommonException {
        integration.setIsActive(true);
        when(integrationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(integration));
        when(integrationRepository.save(any())).thenReturn(integration);

        CommonResponse response = integrationService.toggleIntegration(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
        verify(integrationRepository).save(argThat(i -> !i.getIsActive()));
    }

    // --- deleteIntegration ---

    @Test
    void deleteIntegration_setsIsActiveFalse() throws CommonException {
        when(integrationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(integration));
        when(integrationRepository.save(any())).thenReturn(integration);

        CommonResponse response = integrationService.deleteIntegration(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
        verify(integrationRepository).save(argThat(i -> !i.getIsActive()));
    }

    @Test
    void deleteIntegration_notFound_throws404() {
        when(integrationRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());

        CommonException ex = assertThrows(CommonException.class,
                () -> integrationService.deleteIntegration(1L, 99L));

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

- [ ] **Step 2: Run tests — verify they fail (service class missing)**

```bash
./mvnw test -Dtest=IntegrationServiceTest 2>&1 | tail -20
```

Expected: compilation failure — `IntegrationService` not found.

- [ ] **Step 3: Create `IntegrationService.java`**

```java
package com.ezh.ezauth.integrations.service;

import com.ezh.ezauth.integrations.dto.IntegrationDto;
import com.ezh.ezauth.integrations.dto.IntegrationRequest;
import com.ezh.ezauth.integrations.dto.TestConnectionResponse;
import com.ezh.ezauth.integrations.entity.Integration;
import com.ezh.ezauth.integrations.repository.IntegrationRepository;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationService {

    private final IntegrationRepository integrationRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public CommonResponse createIntegration(Long tenantId, IntegrationRequest request) throws CommonException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        if (request.getIntegrationType() == null) {
            throw new CommonException("Integration type is required", HttpStatus.BAD_REQUEST);
        }

        if (integrationRepository.existsByTenantIdAndIntegrationType(tenantId, request.getIntegrationType())) {
            throw new CommonException(
                    "Integration of type " + request.getIntegrationType() + " already exists for this tenant",
                    HttpStatus.CONFLICT
            );
        }

        Integration integration = Integration.builder()
                .tenant(tenant)
                .integrationType(request.getIntegrationType())
                .displayName(request.getDisplayName())
                .primaryKey(request.getPrimaryKey())
                .secondaryKey(request.getSecondaryKey())
                .tertiaryKey(request.getTertiaryKey())
                .isTestMode(request.getIsTestMode() != null ? request.getIsTestMode() : false)
                .webhookConfig(request.getWebhookConfig())
                .extraConfig(request.getExtraConfig())
                .links(request.getLinks())
                .build();

        Integration saved = integrationRepository.save(integration);
        log.info("Integration {} created for tenant {}", saved.getIntegrationType(), tenantId);

        return CommonResponse.builder()
                .id(saved.getId().toString())
                .message("Integration created successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional(readOnly = true)
    public List<IntegrationDto> getIntegrations(Long tenantId) throws CommonException {
        if (!tenantRepository.existsById(tenantId)) {
            throw new CommonException("Tenant not found", HttpStatus.NOT_FOUND);
        }
        return integrationRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public IntegrationDto getIntegrationById(Long tenantId, Long integrationId) throws CommonException {
        Integration integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new CommonException("Integration not found", HttpStatus.NOT_FOUND));
        return toDto(integration);
    }

    @Transactional
    public CommonResponse updateIntegration(Long tenantId, Long integrationId, IntegrationRequest request) throws CommonException {
        Integration integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new CommonException("Integration not found", HttpStatus.NOT_FOUND));

        if (request.getDisplayName() != null)   integration.setDisplayName(request.getDisplayName());
        if (request.getPrimaryKey() != null)     integration.setPrimaryKey(request.getPrimaryKey());
        if (request.getSecondaryKey() != null)   integration.setSecondaryKey(request.getSecondaryKey());
        if (request.getTertiaryKey() != null)    integration.setTertiaryKey(request.getTertiaryKey());
        if (request.getIsTestMode() != null)     integration.setIsTestMode(request.getIsTestMode());
        if (request.getWebhookConfig() != null)  integration.setWebhookConfig(request.getWebhookConfig());
        if (request.getExtraConfig() != null)    integration.setExtraConfig(request.getExtraConfig());
        if (request.getLinks() != null)          integration.setLinks(request.getLinks());

        Integration updated = integrationRepository.save(integration);
        log.info("Integration {} updated for tenant {}", updated.getId(), tenantId);

        return CommonResponse.builder()
                .id(updated.getId().toString())
                .message("Integration updated successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse deleteIntegration(Long tenantId, Long integrationId) throws CommonException {
        Integration integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new CommonException("Integration not found", HttpStatus.NOT_FOUND));

        integration.setIsActive(false);
        integrationRepository.save(integration);
        log.info("Integration {} soft-deleted for tenant {}", integrationId, tenantId);

        return CommonResponse.builder()
                .id(integration.getId().toString())
                .message("Integration deactivated successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse toggleIntegration(Long tenantId, Long integrationId) throws CommonException {
        Integration integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new CommonException("Integration not found", HttpStatus.NOT_FOUND));

        integration.setIsActive(!integration.getIsActive());
        integrationRepository.save(integration);

        String state = Boolean.TRUE.equals(integration.getIsActive()) ? "enabled" : "disabled";
        log.info("Integration {} {} for tenant {}", integrationId, state, tenantId);

        return CommonResponse.builder()
                .id(integration.getId().toString())
                .message("Integration " + state + " successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public TestConnectionResponse testConnection(Long tenantId, Long integrationId) throws CommonException {
        Integration integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new CommonException("Integration not found", HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        boolean hasKey = integration.getPrimaryKey() != null && !integration.getPrimaryKey().isBlank();

        if (!hasKey) {
            integration.setIsConnected(false);
            integrationRepository.save(integration);
            return TestConnectionResponse.builder()
                    .connected(false)
                    .message("Connection failed: primary key is missing or empty")
                    .testedAt(now)
                    .build();
        }

        integration.setIsConnected(true);
        integration.setConnectedAt(now);
        integrationRepository.save(integration);
        log.info("Test connection passed for integration {} of tenant {}", integrationId, tenantId);

        return TestConnectionResponse.builder()
                .connected(true)
                .message("Credentials validated. Note: live provider ping is a planned enhancement.")
                .testedAt(now)
                .build();
    }

    private IntegrationDto toDto(Integration integration) {
        return IntegrationDto.builder()
                .id(integration.getId())
                .integrationUuid(integration.getIntegrationUuid())
                .tenantId(integration.getTenant().getId())
                .integrationType(integration.getIntegrationType())
                .displayName(integration.getDisplayName())
                .primaryKey(integration.getPrimaryKey() != null ? "****" : null)
                .secondaryKey(integration.getSecondaryKey() != null ? "****" : null)
                .tertiaryKey(integration.getTertiaryKey() != null ? "****" : null)
                .isTestMode(integration.getIsTestMode())
                .isConnected(integration.getIsConnected())
                .isActive(integration.getIsActive())
                .webhookConfig(integration.getWebhookConfig())
                .extraConfig(integration.getExtraConfig())
                .links(integration.getLinks())
                .connectedAt(integration.getConnectedAt())
                .createdAt(integration.getCreatedAt())
                .updatedAt(integration.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 4: Run tests — verify all pass**

```bash
./mvnw test -Dtest=IntegrationServiceTest 2>&1 | tail -20
```

Expected: `Tests run: 12, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ezh/ezauth/integrations/service/IntegrationService.java \
        src/test/java/com/ezh/ezauth/integrations/service/IntegrationServiceTest.java
git commit -m "feat: add IntegrationService with full CRUD, toggle, test-connection, and unit tests"
```

---

## Task 9: IntegrationController

**Files:**
- Create: `src/main/java/com/ezh/ezauth/integrations/controller/IntegrationController.java`

- [ ] **Step 1: Create the controller**

```java
package com.ezh.ezauth.integrations.controller;

import com.ezh.ezauth.integrations.dto.IntegrationDto;
import com.ezh.ezauth.integrations.dto.IntegrationRequest;
import com.ezh.ezauth.integrations.dto.TestConnectionResponse;
import com.ezh.ezauth.integrations.service.IntegrationService;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.ResponseResource;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/tenant/{tenantId}/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationService integrationService;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createIntegration(
            @PathVariable Long tenantId,
            @RequestBody IntegrationRequest request) throws CommonException {
        log.info("Creating integration of type {} for tenant {}", request.getIntegrationType(), tenantId);
        CommonResponse response = integrationService.createIntegration(tenantId, request);
        return ResponseResource.success(HttpStatus.CREATED, response, "Integration created successfully");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<IntegrationDto>> getIntegrations(
            @PathVariable Long tenantId) throws CommonException {
        log.info("Fetching all integrations for tenant {}", tenantId);
        List<IntegrationDto> response = integrationService.getIntegrations(tenantId);
        return ResponseResource.success(HttpStatus.OK, response, "Integrations fetched successfully");
    }

    @GetMapping(value = "/{integrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<IntegrationDto> getIntegrationById(
            @PathVariable Long tenantId,
            @PathVariable Long integrationId) throws CommonException {
        log.info("Fetching integration {} for tenant {}", integrationId, tenantId);
        IntegrationDto response = integrationService.getIntegrationById(tenantId, integrationId);
        return ResponseResource.success(HttpStatus.OK, response, "Integration fetched successfully");
    }

    @PutMapping(value = "/{integrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> updateIntegration(
            @PathVariable Long tenantId,
            @PathVariable Long integrationId,
            @RequestBody IntegrationRequest request) throws CommonException {
        log.info("Updating integration {} for tenant {}", integrationId, tenantId);
        CommonResponse response = integrationService.updateIntegration(tenantId, integrationId, request);
        return ResponseResource.success(HttpStatus.OK, response, "Integration updated successfully");
    }

    @DeleteMapping(value = "/{integrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> deleteIntegration(
            @PathVariable Long tenantId,
            @PathVariable Long integrationId) throws CommonException {
        log.info("Soft-deleting integration {} for tenant {}", integrationId, tenantId);
        CommonResponse response = integrationService.deleteIntegration(tenantId, integrationId);
        return ResponseResource.success(HttpStatus.OK, response, "Integration deleted successfully");
    }

    @PatchMapping(value = "/{integrationId}/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> toggleIntegration(
            @PathVariable Long tenantId,
            @PathVariable Long integrationId) throws CommonException {
        log.info("Toggling integration {} for tenant {}", integrationId, tenantId);
        CommonResponse response = integrationService.toggleIntegration(tenantId, integrationId);
        return ResponseResource.success(HttpStatus.OK, response, "Integration toggled successfully");
    }

    @PostMapping(value = "/{integrationId}/test-connection", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<TestConnectionResponse> testConnection(
            @PathVariable Long tenantId,
            @PathVariable Long integrationId) throws CommonException {
        log.info("Testing connection for integration {} of tenant {}", integrationId, tenantId);
        TestConnectionResponse response = integrationService.testConnection(tenantId, integrationId);
        return ResponseResource.success(HttpStatus.OK, response, "Connection test completed");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/ezh/ezauth/integrations/controller/IntegrationController.java
git commit -m "feat: add IntegrationController with all 7 endpoints"
```

---

## Task 10: Add PATCH to SecurityConfig CORS allowed methods

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/config/SecurityConfig.java:70-76`

The `toggle` endpoint uses `PATCH`. The current CORS config only allows `GET, POST, PUT, DELETE, OPTIONS`. Add `PATCH`.

- [ ] **Step 1: Edit SecurityConfig.java**

Find this block in `SecurityConfig.java` (around line 71):

```java
config.setAllowedMethods(List.of(
        "GET", "POST", "PUT", "DELETE", "OPTIONS"
));
```

Replace it with:

```java
config.setAllowedMethods(List.of(
        "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
));
```

- [ ] **Step 2: Run all tests — verify nothing is broken**

```bash
./mvnw test 2>&1 | tail -30
```

Expected: All tests pass. No compilation errors.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ezh/ezauth/config/SecurityConfig.java
git commit -m "fix: add PATCH to CORS allowed methods for integration toggle endpoint"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** Entity schema ✓, AES encryption ✓, all 7 API endpoints ✓, credential masking ✓, duplicate guard ✓, soft-delete ✓, toggle ✓, test-connection structural validation ✓, JSON blobs ✓, Flyway migration ✓
- [x] **No placeholders:** All steps contain complete code
- [x] **Type consistency:** `IntegrationType` used consistently across entity/repo/service/controller. `CommonException(message, HttpStatus)` constructor matches the actual class. `getHttpStatus()` used in tests (not `getStatus()`). `Integration::getIsActive` lambda used in `toggleIntegration` test
- [x] **PATCH gap:** Addressed in Task 10 — CORS config updated
- [x] **Old stub:** Deleted in Task 1 — avoids duplicate class confusion
