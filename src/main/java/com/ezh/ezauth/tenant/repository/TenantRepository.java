package com.ezh.ezauth.tenant.repository;

import com.ezh.ezauth.common.entity.Application;
import com.ezh.ezauth.tenant.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantCode(String tenantCode);

    List<Tenant> findByIdIn(List<Long> tenantIds);

    Boolean existsByTenantCode(String tenantCode);

    @Query("""
        SELECT t.applications
        FROM Tenant t
        WHERE t.id = :tenantId
    """)
    Set<Application> findApplicationsByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Tenant t JOIN t.applications a WHERE t.id = :tenantId AND a.id = :appId")
    boolean existsTenantApplication(@Param("tenantId") Long tenantId, @Param("appId") Long appId);

    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO auth.tenant_applications (tenant_id, application_id) VALUES (:tenantId, :appId)", nativeQuery = true)
    void linkApplicationToTenant(@Param("tenantId") Long tenantId, @Param("appId") Long appId);

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM auth.tenant_applications WHERE tenant_id = :tenantId AND application_id = :appId", nativeQuery = true)
    void unlinkApplicationFromTenant(@Param("tenantId") Long tenantId, @Param("appId") Long appId);
}
