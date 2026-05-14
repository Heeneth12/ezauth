package com.ezh.ezauth.subscription.service;

import com.ezh.ezauth.subscription.dto.SubscriptionDto;
import com.ezh.ezauth.subscription.dto.SubscriptionPlanDto;
import com.ezh.ezauth.subscription.entity.Subscription;
import com.ezh.ezauth.subscription.entity.SubscriptionPlan;
import com.ezh.ezauth.subscription.entity.SubscriptionStatus;
import com.ezh.ezauth.subscription.repository.SubscriptionPlanRepository;
import com.ezh.ezauth.subscription.repository.SubscriptionRepository;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import com.ezh.ezauth.common.entity.Application;
import com.ezh.ezauth.common.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;

    @Transactional
    public CommonResponse subscribeTenant(Long tenantId, Long planId) throws CommonException {
        log.info("Assigning plan {} to tenant {}", planId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found with id: " + tenantId, HttpStatus.BAD_REQUEST));

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new CommonException("Plan not found with id: " + planId, HttpStatus.BAD_REQUEST));

        subscriptionRepository.findPrimaryActiveSubscription(tenantId, SubscriptionStatus.ACTIVE)
                .ifPresent(oldSub -> {
                    oldSub.setIsPrimary(false);
                    oldSub.setStatus(SubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(oldSub);
                });

        // Create new subscription
        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .isPrimary(true)
                .startDate(now)
                .endDate(now.plusDays(plan.getDurationDays()))
                .autoRenew(true)
                .build();

        subscriptionRepository.save(subscription);

        return CommonResponse.builder()
                .id(tenantId.toString())
                .status(Status.SUCCESS)
                .message("Tenant subscribed successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public SubscriptionDto getTenantSubscription(Long tenantId) throws CommonException {
        if (!tenantRepository.existsById(tenantId)) {
            throw new CommonException("Tenant not found with id: " + tenantId, HttpStatus.BAD_REQUEST);
        }

        Subscription subscription = subscriptionRepository
                .findPrimaryActiveSubscription(tenantId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new CommonException("No active subscription found for tenant.", HttpStatus.BAD_REQUEST));

        return mapToDto(subscription);
    }

    @Transactional
    public CommonResponse cancelSubscription(Long subscriptionId) throws CommonException {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new CommonException("Subscription not found", HttpStatus.BAD_REQUEST));

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setAutoRenew(false);
        subscription = subscriptionRepository.save(subscription);

        return CommonResponse.builder()
                .id(subscription.getId().toString())
                .status(Status.SUCCESS)
                .message("Subscription cancelled successfully")
                .build();
    }


    @Transactional
    public CommonResponse createPlan(SubscriptionPlanDto dto) throws CommonException {

        log.info("Creating new subscription plan: {}", dto.getName());

        Application application = applicationRepository.findById(dto.getApplicationId())
                .orElseThrow(() -> new CommonException("Application not found with id: " + dto.getApplicationId(), HttpStatus.BAD_REQUEST));

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .application(application)
                .name(dto.getName())
                .description(dto.getDescription())
                .type(dto.getType())
                .price(dto.getPrice())
                .durationDays(dto.getDurationDays())
                .maxUsers(dto.getMaxUsers())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        plan = planRepository.save(plan);
        return CommonResponse
                .builder()
                .id(plan.getId().toString())
                .status(Status.SUCCESS)
                .message("Subscription plan created successfully")
                .build();
    }

    @Transactional
    public CommonResponse editPlan(Long planId, SubscriptionPlanDto dto) throws CommonException {
        log.info("Editing subscription plan: {}", planId);

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new CommonException("Subscription plan not found with id: " + planId, HttpStatus.BAD_REQUEST));

        Application application = applicationRepository.findById(dto.getApplicationId())
                .orElseThrow(() -> new CommonException("Application not found with id: " + dto.getApplicationId(), HttpStatus.BAD_REQUEST));

        plan.setApplication(application);
        plan.setName(dto.getName());
        plan.setDescription(dto.getDescription());
        plan.setType(dto.getType());
        plan.setPrice(dto.getPrice());
        plan.setDurationDays(dto.getDurationDays());
        plan.setMaxUsers(dto.getMaxUsers());
        
        if (dto.getIsActive() != null) {
            plan.setIsActive(dto.getIsActive());
        }

        planRepository.save(plan);

        return CommonResponse.builder()
                .id(plan.getId().toString())
                .status(Status.SUCCESS)
                .message("Subscription plan updated successfully")
                .build();
    }

    @Transactional
    public CommonResponse deletePlan(Long planId) throws CommonException {
        log.info("Deleting subscription plan: {}", planId);

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new CommonException("Subscription plan not found with id: " + planId, HttpStatus.BAD_REQUEST));

        planRepository.delete(plan);

        return CommonResponse.builder()
                .id(planId.toString())
                .status(Status.SUCCESS)
                .message("Subscription plan deleted successfully")
                .build();
    }

    @Transactional
    public CommonResponse disablePlan(Long planId) throws CommonException {
        log.info("Disabling subscription plan: {}", planId);

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new CommonException("Subscription plan not found with id: " + planId, HttpStatus.BAD_REQUEST));

        plan.setIsActive(false);
        planRepository.save(plan);

        return CommonResponse.builder()
                .id(planId.toString())
                .status(Status.SUCCESS)
                .message("Subscription plan disabled successfully")
                .build();
    }

    public List<SubscriptionPlanDto> getActivePlans() {
        return planRepository.findByIsActiveTrue().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public SubscriptionPlanDto getPlanById(Long id) throws CommonException {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new CommonException("Subscription plan not found with id: " + id, HttpStatus.BAD_REQUEST));
        return mapToDto(plan);
    }

    /**
     * Checks if the given tenant has an active and unexpired subscription.
     */
    public Boolean hasValidSubscription(Long tenantId) {
        return subscriptionRepository.findValidSubscription(tenantId, SubscriptionStatus.ACTIVE, LocalDateTime.now()).isPresent();
    }

    // Helper mapper
    private SubscriptionPlanDto mapToDto(SubscriptionPlan plan) {
        return SubscriptionPlanDto.builder()
                .id(plan.getId())
                .applicationId(plan.getApplication().getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .type(plan.getType())
                .price(plan.getPrice())
                .durationDays(plan.getDurationDays())
                .maxUsers(plan.getMaxUsers())
                .isActive(plan.getIsActive())
                .build();
    }

    // Helper mapper
    private SubscriptionDto mapToDto(Subscription subscription) {
        long daysRemaining = 0;
        if (subscription.getEndDate() != null && LocalDateTime.now().isBefore(subscription.getEndDate())) {
            daysRemaining = Duration.between(LocalDateTime.now(), subscription.getEndDate()).toDays();
        }

        SubscriptionPlan plan = subscription.getPlan();
        SubscriptionPlanDto planDto = SubscriptionPlanDto.builder()
                .id(plan.getId())
                .applicationId(plan.getApplication().getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .type(plan.getType())
                .price(plan.getPrice())
                .durationDays(plan.getDurationDays())
                .maxUsers(plan.getMaxUsers())
                .build();

        return SubscriptionDto.builder()
                .id(subscription.getId())
                .plan(planDto)
                .status(subscription.getStatus())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .autoRenew(subscription.getAutoRenew())
                .createdAt(subscription.getCreatedAt())
                .isValid(subscription.isValid())
                .daysRemaining(daysRemaining)
                .build();
    }
}