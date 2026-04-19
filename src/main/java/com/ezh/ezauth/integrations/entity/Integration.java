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
