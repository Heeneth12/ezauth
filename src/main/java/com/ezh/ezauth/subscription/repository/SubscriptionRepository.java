package com.ezh.ezauth.subscription.repository;

import com.ezh.ezauth.subscription.entity.Subscription;
import com.ezh.ezauth.subscription.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByTenantIdAndStatus(Long tenantId, SubscriptionStatus status);

    @Query("SELECT s FROM Subscription s WHERE s.tenant.id = :tenantId " +
            "AND s.isPrimary = true " +
            "AND s.status = :status")
    Optional<Subscription> findPrimaryActiveSubscription(@Param("tenantId") Long tenantId,
                                                         @Param("status") SubscriptionStatus status);

    // Using boolean logic and status check
    @Query("""
            SELECT s FROM Subscription s WHERE s.tenant.id = :tenantId
                    AND s.status = :status 
                    AND s.isPrimary = true
                    AND s.endDate > :now
            """)
    Optional<Subscription> findValidSubscription(
            @Param("tenantId") Long tenantId,
            @Param("status") SubscriptionStatus status,
            @Param("now") LocalDateTime now
    );
}