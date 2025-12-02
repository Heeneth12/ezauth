package com.ezh.ezauth.tenant.repository;

import com.ezh.ezauth.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantCode(String tenantCode);

    Boolean existsByTenantCode(String tenantCode);
}
