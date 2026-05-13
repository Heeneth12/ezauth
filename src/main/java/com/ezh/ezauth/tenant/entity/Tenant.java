package com.ezh.ezauth.tenant.entity;

import com.ezh.ezauth.branch.entity.Branch;
import com.ezh.ezauth.common.entity.Address;
import com.ezh.ezauth.common.entity.Application;
import com.ezh.ezauth.common.entity.Role;
import com.ezh.ezauth.subscription.entity.Subscription;
import com.ezh.ezauth.user.entity.User;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_uuid", nullable = false, unique = true, updatable = false)
    private String tenantUuid;

    @Column(name = "tenant_name", nullable = false)
    private String tenantName;

    @Column(name = "tenant_code", nullable = false, unique = true, updatable = false)
    private String tenantCode;

    @Builder.Default
    @Column(name = "is_personal", nullable = false)
    private Boolean isPersonal = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "is_verify", nullable = false)
    private Boolean isVerify = false;

    // Tenant owner
    // Set after user creation (circular ref resolved via ALTER TABLE in migration)
    // Always TENANT_ADMIN type, branch_id = null
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_admin_user_id")
    private User tenantAdmin;

    // Details (1:1)
    @OneToOne(mappedBy = "tenant",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private TenantDetails tenantDetails;

    // Branches
    @OneToMany(mappedBy = "tenant",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<Branch> branches;

    // Users
    // Navigate via repo in most cases
    @OneToMany(mappedBy = "tenant",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<User> users;

    // Roles (templates)
    @OneToMany(mappedBy = "tenant",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<Role> roles;

    // Subscriptions
    // No current_subscription_id column — derive active sub via repo:
    // subscriptionRepo.findFirstByTenantIdAndStatusIn(id, List.of("ACTIVE","TRIAL"))
    @OneToMany(mappedBy = "tenant",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<Subscription> subscriptions;

    // Subscribed applications
    // tenant_applications join table — which apps this tenant has access to
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "tenant_applications",
            schema = "auth",
            joinColumns = @JoinColumn(name = "tenant_id"),
            inverseJoinColumns = @JoinColumn(name = "application_id")
    )
    private Set<Application> applications;

    // Addresses
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id",
            insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @SQLRestriction("entity_type = 'TENANT'")
    private Set<Address> addresses;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (tenantUuid == null) {
            tenantUuid = UUID.randomUUID().toString();
        }
    }
}