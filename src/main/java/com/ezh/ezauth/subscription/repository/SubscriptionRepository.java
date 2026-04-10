package com.ezh.ezauth.subscription.repository;

import com.ezh.ezauth.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByTenantIdAndStatus(Long tenantId, String status);
}
