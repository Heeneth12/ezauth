package com.ezh.ezauth.tenant.repository;

import com.ezh.ezauth.common.entity.Application;
import com.ezh.ezauth.tenant.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantCode(String tenantCode);

    Boolean existsByTenantCode(String tenantCode);

    @Query("""
        SELECT t.applications 
        FROM Tenant t 
        WHERE t.id = :tenantId
    """)
    Set<Application> findApplicationsByTenantId(@Param("tenantId") Long tenantId);
}
