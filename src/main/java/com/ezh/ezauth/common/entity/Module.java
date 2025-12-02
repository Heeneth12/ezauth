package com.ezh.ezauth.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "modules", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"application_id", "module_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Module {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String moduleName; // e.g., "Dashboard", "Billing"

    @Column(nullable = false)
    private String moduleKey; // e.g., "DASHBOARD", "BILLING"

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL)
    private Set<Privilege> privileges;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}