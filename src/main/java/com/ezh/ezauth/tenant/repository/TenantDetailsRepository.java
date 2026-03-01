package com.ezh.ezauth.tenant.repository;

import com.ezh.ezauth.tenant.entity.TenantDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantDetailsRepository extends JpaRepository<TenantDetails, Long> {

    Optional<TenantDetails> findByTenantId(Long tenantId);
}
