package com.ezh.ezauth.common.repository;

import com.ezh.ezauth.common.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findByTenantId(Long tenantId);
}
