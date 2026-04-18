package com.ezh.ezauth.integrations.controller;

import com.ezh.ezauth.integrations.dto.IntegrationDto;
import com.ezh.ezauth.integrations.dto.IntegrationRequest;
import com.ezh.ezauth.integrations.dto.TestConnectionResponse;
import com.ezh.ezauth.integrations.entity.IntegrationType;
import com.ezh.ezauth.integrations.service.IntegrationService;
import com.ezh.ezauth.utils.UserContextUtil;
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
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationService integrationService;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createIntegration(
            @RequestBody IntegrationRequest request) throws CommonException {
        log.info("Creating integration of type {}", request.getIntegrationType());
        CommonResponse response = integrationService.createIntegration(request);
        return ResponseResource.success(HttpStatus.CREATED, response, "Integration created successfully");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<IntegrationDto>> getIntegrations() throws CommonException {
        log.info("Fetching all integrations");
        Long tenantId = UserContextUtil.getTenantIdOrThrow();
        List<IntegrationDto> response = integrationService.getIntegrations(tenantId);
        return ResponseResource.success(HttpStatus.OK, response, "Integrations fetched successfully");
    }

    @GetMapping(value = "/by-type", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<IntegrationDto> getIntegrationByType(@RequestParam IntegrationType type)
            throws CommonException {
        Long tenantId = UserContextUtil.getTenantIdOrThrow();
        log.info("Fetching integration for type: {} and tenant: {}", type, tenantId);

        IntegrationDto response = integrationService.getIntegrationByType(tenantId, type);
        return ResponseResource.success(HttpStatus.OK, response, "Integration fetched successfully");
    }

    @GetMapping(value = "/check", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> checkIntegration(@RequestParam IntegrationType type)
            throws CommonException {
        Long tenantId = UserContextUtil.getTenantIdOrThrow();
        log.info("Checking integration for type: {} and tenant: {}", type, tenantId);
        CommonResponse response = integrationService.checkIntegration(tenantId, type);
        return ResponseResource.success(HttpStatus.OK, response, "Integration checked successfully");
    }

    @GetMapping(value = "/{integrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<IntegrationDto> getIntegrationById(
            @PathVariable Long integrationId) throws CommonException {
        Long tenantId = UserContextUtil.getTenantIdOrThrow();
        log.info("Fetching integration {} for tenant {}", integrationId, tenantId);
        IntegrationDto response = integrationService.getIntegrationById(tenantId, integrationId);
        return ResponseResource.success(HttpStatus.OK, response, "Integration fetched successfully");
    }

    @PostMapping(value = "/{integrationId}/update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> updateIntegration(@PathVariable Long integrationId,
            @RequestBody IntegrationRequest request) throws CommonException {
        Long tenantId = UserContextUtil.getTenantIdOrThrow();
        log.info("Updating integration {} for tenant {}", integrationId, tenantId);
        CommonResponse response = integrationService.updateIntegration(tenantId, integrationId, request);
        return ResponseResource.success(HttpStatus.OK, response, "Integration updated successfully");
    }

    @PostMapping(value = "/{integrationId}/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> deleteIntegration(@PathVariable Long integrationId) throws CommonException {
        Long tenantId = UserContextUtil.getTenantIdOrThrow();
        log.info("Soft-deleting integration {} for tenant {}", integrationId, tenantId);
        CommonResponse response = integrationService.deleteIntegration(tenantId, integrationId);
        return ResponseResource.success(HttpStatus.OK, response, "Integration deleted successfully");
    }

    @PatchMapping(value = "/{integrationId}/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> toggleIntegration(
            @PathVariable Long integrationId) throws CommonException {
        Long tenantId = UserContextUtil.getTenantIdOrThrow();
        log.info("Toggling integration {} for tenant {}", integrationId, tenantId);
        CommonResponse response = integrationService.toggleIntegration(tenantId, integrationId);
        return ResponseResource.success(HttpStatus.OK, response, "Integration toggled successfully");
    }

    @PostMapping(value = "/{integrationId}/test-connection", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<TestConnectionResponse> testConnection(
            @PathVariable Long integrationId) throws CommonException {
        Long tenantId = UserContextUtil.getTenantIdOrThrow();
        log.info("Testing connection for integration {} of tenant {}", integrationId, tenantId);
        TestConnectionResponse response = integrationService.testConnection(tenantId, integrationId);
        return ResponseResource.success(HttpStatus.OK, response, "Connection test completed");
    }
}
