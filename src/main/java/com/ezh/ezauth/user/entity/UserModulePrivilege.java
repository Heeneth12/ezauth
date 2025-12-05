package com.ezh.ezauth.user.entity;

import com.ezh.ezauth.common.entity.Module;
import com.ezh.ezauth.common.entity.Privilege;
import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;

@Entity
@Table(
        name = "user_module_privileges",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_application_id", "module_id", "privilege_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserModulePrivilege {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_application_id", nullable = false)
    private UserApplication userApplication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "privilege_id", nullable = false)
    private Privilege privilege;

    @Column(nullable = false)
    private Boolean isActive = true;

    // --------------------------------------------------------
    // IDENTITY LOGIC (Fixes the Duplicate Entry Error)
    // --------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserModulePrivilege that = (UserModulePrivilege) o;

        // 1. If both have IDs, match by ID
        if (this.id != null && that.id != null) {
            return Objects.equals(this.id, that.id);
        }

        // 2. Otherwise match by Business Key (UserApp + Module + Privilege)
        return Objects.equals(userApplication, that.userApplication) &&
                Objects.equals(module, that.module) &&
                Objects.equals(privilege, that.privilege);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userApplication, module, privilege);
    }
}