package com.ezh.ezauth.integrations.repository;

import com.ezh.ezauth.integrations.entity.Integration;
import com.ezh.ezauth.integrations.entity.IntegrationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    List<Integration> findByTenantId(Long tenantId);

    Optional<Integration> findByTenantIdAndIntegrationType(Long tenantId, IntegrationType integrationType);

    Optional<Integration> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndIntegrationType(Long tenantId, IntegrationType integrationType);
}
