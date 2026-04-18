package com.ezh.ezauth.subscription.repository;

import com.ezh.ezauth.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByTenantIdAndStatus(Long tenantId, String status);

    // Fetches a subscription that is currently ACTIVE and has not passed its end date
    @Query("SELECT s FROM Subscription s WHERE s.tenant.id = :tenantId " +
            "AND s.status = 'ACTIVE' " +
            "AND s.endDate > CURRENT_TIMESTAMP")
    Optional<Subscription> findValidSubscriptionByTenantId(@Param("tenantId") Long tenantId);
}
