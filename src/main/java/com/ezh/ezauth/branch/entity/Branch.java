package com.ezh.ezauth.branch.entity;

import com.ezh.ezauth.common.entity.Address;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.entity.UserRole;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "branch_name", nullable = false)
    private String branchName;

    @Column(name = "branch_code", nullable = false)
    private String branchCode;


    @Builder.Default
    @Column(name = "is_head_office", nullable = false)
    private Boolean isHeadOffice = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Users in this branch
    // Don't load this directly — use userRepo.findByBranchId() instead
    @OneToMany(mappedBy = "branch", fetch = FetchType.LAZY)
    private Set<User> users;

    // Branch-scoped role assignments
    // user_roles rows where branch_id = this branch's id
    @OneToMany(mappedBy = "branch",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<UserRole> userRoles;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id",
            insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @SQLRestriction("entity_type = 'BRANCH'")
    private Set<Address> addresses;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}