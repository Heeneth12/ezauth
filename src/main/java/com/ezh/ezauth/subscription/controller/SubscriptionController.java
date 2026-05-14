package com.ezh.ezauth.subscription.controller;

import com.ezh.ezauth.subscription.dto.SubscriptionDto;
import com.ezh.ezauth.subscription.dto.SubscriptionPlanDto;
import com.ezh.ezauth.subscription.service.SubscriptionService;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.ResponseResource;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping(value = "/tenant/{tenantId}/plan/{planId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> subscribeTenant(
            @PathVariable Long tenantId,
            @PathVariable Long planId) throws CommonException {
        log.info("Entered subscribe tenant {} to plan {}", tenantId, planId);
        CommonResponse response = subscriptionService.subscribeTenant(tenantId, planId);
        return ResponseResource.success(HttpStatus.CREATED, response, "Tenant subscribed successfully");
    }

    @GetMapping(value = "/tenant/{tenantId}/current", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<SubscriptionDto> getCurrentSubscription(@PathVariable Long tenantId) throws CommonException {
        log.info("Entered get current subscription for tenant: {}", tenantId);
        SubscriptionDto response = subscriptionService.getTenantSubscription(tenantId);
        return ResponseResource.success(HttpStatus.OK, response, "Current subscription fetched successfully");
    }

    @PutMapping(value = "/{subscriptionId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> cancelSubscription(@PathVariable Long subscriptionId) throws CommonException {
        log.info("Entered cancel subscription for id: {}", subscriptionId);
        CommonResponse response = subscriptionService.cancelSubscription(subscriptionId);
        return ResponseResource.success(HttpStatus.OK, response, "Subscription cancelled successfully");
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createPlan(@RequestBody SubscriptionPlanDto planDto) throws CommonException {
        log.info("Entered create subscription plan: {}", planDto);
        CommonResponse response = subscriptionService.createPlan(planDto);
        return ResponseResource.success(HttpStatus.CREATED, response, "Subscription plan created successfully");
    }

    @PutMapping(value = "/plan/{planId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> editPlan(@PathVariable Long planId, @RequestBody SubscriptionPlanDto planDto) throws CommonException {
        log.info("Entered edit subscription plan for id: {}", planId);
        CommonResponse response = subscriptionService.editPlan(planId, planDto);
        return ResponseResource.success(HttpStatus.OK, response, "Subscription plan updated successfully");
    }

    @DeleteMapping(value = "/plan/{planId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> deletePlan(@PathVariable Long planId) throws CommonException {
        log.info("Entered delete subscription plan for id: {}", planId);
        CommonResponse response = subscriptionService.deletePlan(planId);
        return ResponseResource.success(HttpStatus.OK, response, "Subscription plan deleted successfully");
    }

    @PatchMapping(value = "/plan/{planId}/disable", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> disablePlan(@PathVariable Long planId) throws CommonException {
        log.info("Entered disable subscription plan for id: {}", planId);
        CommonResponse response = subscriptionService.disablePlan(planId);
        return ResponseResource.success(HttpStatus.OK, response, "Subscription plan disabled successfully");
    }

    @GetMapping(value = "/active", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<SubscriptionPlanDto>> getActivePlans() throws CommonException {
        log.info("Entered get all active subscription plans");
        List<SubscriptionPlanDto> response = subscriptionService.getActivePlans();
        return ResponseResource.success(HttpStatus.OK, response, "Active subscription plans fetched successfully");
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<SubscriptionPlanDto> getPlanById(@PathVariable Long id) throws CommonException {
        log.info("Entered get subscription plan by id: {}", id);
        SubscriptionPlanDto response = subscriptionService.getPlanById(id);
        return ResponseResource.success(HttpStatus.OK, response, "Subscription plan fetched successfully");
    }
}