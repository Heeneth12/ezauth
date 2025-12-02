package com.ezh.ezauth.user.entity;
import com.ezh.ezauth.common.entity.Module;
import com.ezh.ezauth.common.entity.Privilege;
import jakarta.persistence.*;
import lombok.*;

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

    // Link to user + application combination
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
}
