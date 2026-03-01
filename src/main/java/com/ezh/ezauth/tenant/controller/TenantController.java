package com.ezh.ezauth.tenant.controller;

import com.ezh.ezauth.tenant.dto.*;
import com.ezh.ezauth.tenant.service.TenantService;
import com.ezh.ezauth.user.dto.UserDto;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.ResponseResource;
import com.ezh.ezauth.utils.exception.CommonException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<Page<TenantDto>> getAllTenants(@RequestParam Integer page, @RequestParam Integer size) throws CommonException {
        log.info("Entered get all tenants details with filter");
        Page<TenantDto> response = tenantService.getTenants(page, size);
        return ResponseResource.success(HttpStatus.OK, response, "All tenants fetched successfully");
    }

    @GetMapping(value = "/bulk", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<Map<Long, TenantDto>> getBulkTenants(@RequestParam("ids") List<Long> ids) throws CommonException {
        log.info("Entered get bulk tenants details");
        Map<Long, TenantDto> response = tenantService.getTenantsByIds(ids);
        return ResponseResource.success(HttpStatus.OK, response, "Bulk tenants fetched successfully");
    }

    @PostMapping(value = "/{tenantId}/update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> updateTenant(@PathVariable Long tenantId, @RequestBody TenantRegistrationRequest request) throws CommonException {
        log.info("Entered updateTenant details with : {}", request);
        CommonResponse response = tenantService.updateTenant(tenantId, request);
        return ResponseResource.success(HttpStatus.OK, response, "Tenants updated successfully");
    }

    @GetMapping(value = "/{tenantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<TenantDto> getTenantById(@PathVariable Long tenantId) {
        log.info("Fetching tenant details for ID: {}", tenantId);
        TenantDto response = tenantService.getTenantById(tenantId);
        return ResponseResource.success(HttpStatus.OK, response, "Tenant details fetched successfully");
    }

    @PostMapping(value = "/{tenantId}/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createTenantDetails(
            @PathVariable Long tenantId,
            @Valid @RequestBody TenantDetailsDto request) throws CommonException {
        log.info("Creating business details for tenant ID: {}", tenantId);
        CommonResponse response = tenantService.createTenantDetails(tenantId, request);
        return ResponseResource.success(HttpStatus.CREATED, response, "Tenant business details created successfully");
    }

    @PutMapping(value = "/{tenantId}/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> updateTenantDetails(
            @PathVariable Long tenantId,
            @Valid @RequestBody TenantDetailsDto request) throws CommonException {
        log.info("Updating business details for tenant ID: {}", tenantId);
        CommonResponse response = tenantService.updateTenantDetails(tenantId, request);
        return ResponseResource.success(HttpStatus.OK, response, "Tenant business details updated successfully");
    }

    @GetMapping(value = "/{tenantId}/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<TenantDetailsDto> getTenantDetails(@PathVariable Long tenantId) throws CommonException {
        log.info("Fetching business details for tenant ID: {}", tenantId);
        TenantDetailsDto response = tenantService.getTenantDetailsByTenantId(tenantId);
        return ResponseResource.success(HttpStatus.OK, response, "Tenant business details fetched successfully");
    }

}