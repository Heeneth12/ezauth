package com.ezh.ezauth.support.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_req_uuid")
    private String userReqUuid;

    @Column(name = "tenant_uuid")
    private String tenantUuid;

    @Column(name = "user_uuid")
    private String userUuid;

    @Column(name = "assigned_uuid")
    private String assignedUuid;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_name")
    private String contactName;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private SupportCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SupportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private SupportPriority priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    public void prePersist() {
        if (userReqUuid == null) {
            userReqUuid = UUID.randomUUID().toString();
        }
    }
}