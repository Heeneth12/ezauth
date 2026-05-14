package com.ezh.ezauth.user.entity;

import com.ezh.ezauth.branch.entity.Branch;
import com.ezh.ezauth.common.entity.Address;
import com.ezh.ezauth.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_uuid", nullable = false, unique = true, updatable = false)
    private String userUuid;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, updatable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "phone")
    private String phone;

    @Column(name = "profile_picture_uuid")
    private String profilePictureUuid;

    // User type
    // TENANT_ADMIN → branch must be null (enforced by DB CHECK + service layer)
    // Others       → branch must be set
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 50)
    private UserType userType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "account_scope", nullable = false, length = 50)
    private AccountScope accountScope = AccountScope.TENANT;

    @Builder.Default
    @Column(name = "is_login_enabled", nullable = false)
    private Boolean isLoginEnabled = true;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Tenant
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // Branch
    // NULL  = TENANT_ADMIN (no branch scope)
    // SET   = branch-scoped user (BRANCH_MANAGER / STAFF / GUEST)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    // Application access
    // Each entry = one app this user can access + their privilege list
    @OneToMany(mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<UserApplication> userApplications;

    // Role assignments (template only, not runtime enforced)
    @OneToMany(mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<UserRole> userRoles;

    // Addresses (polymorphic)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id",
            insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @SQLRestriction("entity_type = 'USER'")
    private Set<Address> addresses;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (userUuid == null) {
            userUuid = UUID.randomUUID().toString();
        }
    }
}