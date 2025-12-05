package com.ezh.ezauth.user.entity;

import com.ezh.ezauth.common.entity.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "user_roles", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "role_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime assignedAt;

    private LocalDateTime expiresAt;

    // --- ADD THESE METHODS ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserRole userRole = (UserRole) o;

        // 1. If both have IDs, compare IDs (fastest/safest for DB objects)
        if (this.id != null && userRole.id != null) {
            return Objects.equals(this.id, userRole.id);
        }

        // 2. If one is new (null ID), compare the Business Key (User + Role)
        // This allows the Set to know that a "New UserRole Request" is the same as "Existing UserRole"
        return Objects.equals(user, userRole.user) &&
                Objects.equals(role, userRole.role);
    }

    @Override
    public int hashCode() {
        // Only hash the Business Key, never the ID (as ID changes after save)
        return Objects.hash(user, role);
    }
}