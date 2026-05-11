package com.ezh.ezauth.tenant.entity;

import com.ezh.ezauth.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "tenant_branches",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tenant_branch_code", columnNames = {"tenant_id", "branch_code"}),
                @UniqueConstraint(name = "uk_tenant_branch_name", columnNames = {"tenant_id", "branch_name"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_uuid", nullable = false, unique = true, updatable = false)
    private String branchUuid;

    @Column(name = "branch_name", nullable = false)
    private String branchName;

    @Column(name = "branch_code", nullable = false, updatable = false)
    private String branchCode;

    @Column(name = "description")
    private String description;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "route")
    private String route;

    @Column(name = "area")
    private String area;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "country")
    private String country;

    @Column(name = "pin_code")
    private String pinCode;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @OneToMany(mappedBy = "branch")
    private Set<User> users;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (branchUuid == null) {
            branchUuid = UUID.randomUUID().toString();
        }
    }
}
