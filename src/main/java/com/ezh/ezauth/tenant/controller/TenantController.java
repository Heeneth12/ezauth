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

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> registerTenant(
            @Valid @RequestBody TenantRegistrationRequest request) {

        try {
            TenantRegistrationResponse response = tenantService.registerTenant(request);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.builder()
                            .success(true)
                            .message("Tenant registered successfully")
                            .data(response)
                            .build()
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .data(null)
                            .build()
            );
        }
    }

    @PostMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<Page<TenantDto>> getAllUsers(@RequestParam Integer page, @RequestParam Integer size) throws CommonException {
        log.info("Entered get all tenants details");
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
    public ResponseResource<CommonResponse> getAllUsers(@PathVariable Long tenantId, @RequestBody TenantRegistrationRequest request) throws CommonException {
        log.info("Entered get all tenants details");
        CommonResponse response = tenantService.updateTenant(tenantId, request);
        return ResponseResource.success(HttpStatus.OK, response, "All tenants fetched successfully");
    }

    @GetMapping(value = "/{tenantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<TenantDto> getTenantById(@PathVariable Long tenantId) {
        log.info("Fetching tenant details for ID: {}", tenantId);
        TenantDto response = tenantService.getTenantById(tenantId);
        return ResponseResource.success(HttpStatus.OK, response, "Tenant details fetched successfully");
    }

}