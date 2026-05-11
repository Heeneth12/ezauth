package com.ezh.ezauth.tenant.repository;

import com.ezh.ezauth.tenant.entity.TenantBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantBranchRepository extends JpaRepository<TenantBranch, Long> {
    List<TenantBranch> findByTenant_IdOrderByBranchNameAsc(Long tenantId);

    Optional<TenantBranch> findByIdAndTenant_Id(Long id, Long tenantId);

    boolean existsByTenant_IdAndBranchCode(Long tenantId, String branchCode);

    boolean existsByTenant_IdAndBranchNameIgnoreCase(Long tenantId, String branchName);
}
