package com.ezh.ezauth.tenant.entity;


import com.ezh.ezauth.common.entity.Application;
import com.ezh.ezauth.common.entity.Role;
import com.ezh.ezauth.user.entity.User;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
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

    @Column(name = "tenant_uuid", nullable = false)
    private String tenantUuid;

    @Column(name = "tenant_name", nullable = false)
    private String tenantName;

    @Column(name = "tenant_code", nullable = false, unique = true, updatable = false)
    private String tenantCode;

    @Column(name = "is_personal", nullable = false)
    private Boolean isPersonal = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Tenant admin (Owner user)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_admin_user_id")
    private User tenantAdmin;

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL)
    private Set<User> users;

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL)
    private Set<Role> roles;

    @ManyToMany
    @JoinTable(
            name = "tenant_applications",
            joinColumns = @JoinColumn(name = "tenant_id"),
            inverseJoinColumns = @JoinColumn(name = "application_id")
    )
    private Set<Application> applications;

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
