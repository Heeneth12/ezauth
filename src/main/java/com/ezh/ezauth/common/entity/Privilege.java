package com.ezh.ezauth.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "privileges", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"module_id", "privilege_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Privilege {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String privilegeName; // e.g., "Read Only", "Edit"

    @Column(nullable = false)
    private String privilegeKey; // e.g., "READ", "WRITE", "DELETE"

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}