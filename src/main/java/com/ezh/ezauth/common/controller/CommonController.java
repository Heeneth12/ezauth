package com.ezh.ezauth.common.controller;


import com.ezh.ezauth.common.dto.AddressDto;
import com.ezh.ezauth.common.dto.ApplicationDto;
import com.ezh.ezauth.common.dto.RoleDto;
import com.ezh.ezauth.common.entity.EntityType;
import com.ezh.ezauth.common.service.AddressService;
import com.ezh.ezauth.common.service.CommonService;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.ResponseResource;
import com.ezh.ezauth.utils.exception.CommonException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/common")
@RequiredArgsConstructor
public class CommonController {

    private final CommonService commonService;
    private final AddressService addressService;

    @GetMapping(value = "/app/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<ApplicationDto>> getAllApplication() throws CommonException {
        log.info("Entered get all applications");
        List<ApplicationDto> response = commonService.getAllApplications();
        return ResponseResource.success(HttpStatus.CREATED, response, "Tenant registered successfully");
    }

    @GetMapping(value = "/role/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<RoleDto>> getAllRoles() throws CommonException {
        log.info("Entered get all roles");
        List<RoleDto> response = commonService.getAllRoles();
        return ResponseResource.success(HttpStatus.CREATED, response, "User roles fetched  successfully");
    }

    @PostMapping(value = "/app/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createApplication(@Valid @RequestBody ApplicationDto dto) throws CommonException {
        log.info("Entered create application");
        CommonResponse response = commonService.createApplication(dto);
        return ResponseResource.success(HttpStatus.CREATED, response, "Application created successfully");
    }

    @PutMapping(value = "/app/{appId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> updateApplication(@PathVariable Long appId, @Valid @RequestBody ApplicationDto dto) throws CommonException {
        log.info("Entered update application id={}", appId);
        CommonResponse response = commonService.updateApplication(appId, dto);
        return ResponseResource.success(HttpStatus.OK, response, "Application updated successfully");
    }

    @DeleteMapping(value = "/app/{appId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> deleteApplication(@PathVariable Long appId) throws CommonException {
        log.info("Entered delete application id={}", appId);
        CommonResponse response = commonService.deleteApplication(appId);
        return ResponseResource.success(HttpStatus.OK, response, "Application removed successfully");
    }

    @PostMapping(value = "/role/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createRole(@RequestBody RoleDto roleDto) throws CommonException {
        log.info("Entered create role");
        CommonResponse response = commonService.createRole(roleDto);
        return ResponseResource.success(HttpStatus.CREATED, response, "Role created successfully");
    }

    @PutMapping(value = "/role/{roleId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> updateRole(@PathVariable Long roleId, @RequestBody RoleDto dto) throws CommonException {
        log.info("Entered update role id={}", roleId);
        CommonResponse response = commonService.updateRole(roleId, dto);
        return ResponseResource.success(HttpStatus.OK, response, "Role updated successfully");
    }

    @DeleteMapping(value = "/role/{roleId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> deleteRole(@PathVariable Long roleId) throws CommonException {
        log.info("Entered delete role id={}", roleId);
        CommonResponse response = commonService.deleteRole(roleId);
        return ResponseResource.success(HttpStatus.OK, response, "Role deleted successfully");
    }

    @GetMapping(value = "/user-types", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<String>> getUserTypes() throws CommonException {
        log.info("Entered get user types");
        List<String> response = commonService.getUserTypes();
        return ResponseResource.success(HttpStatus.OK, response, "User types fetched successfully");
    }

    @GetMapping(value = "/apps/{appId}/modules", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<?> getModulesByApplication(@PathVariable Long appId) throws CommonException{
        log.info("Entered get modules for appId: {}", appId);
        Object response = commonService.getModulesByApplication(appId);
        return ResponseResource.success(HttpStatus.OK, response, "Modules fetched successfully");
    }

    @GetMapping(value = "/modules/{moduleId}/privileges", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<?> getPrivilegesByModule(@PathVariable Long moduleId) throws CommonException{
        log.info("Entered get privileges for moduleId: {}", moduleId);
        Object response = new Object();
        return ResponseResource.success(HttpStatus.OK, response, "Privileges fetched successfully");
    }

    // ─── Address APIs ────────────────────────────────────────────────────────

    @GetMapping(value = "/address/types", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<String>> getAddressTypes() {
        return ResponseResource.success(HttpStatus.OK, addressService.getAddressTypes(), "Address types fetched successfully");
    }

    @GetMapping(value = "/address/{entityType}/{entityId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<AddressDto>> getAddresses(
            @PathVariable EntityType entityType,
            @PathVariable Long entityId) throws CommonException {
        log.info("Fetching addresses for {}:{}", entityType, entityId);
        return ResponseResource.success(HttpStatus.OK, addressService.getAddresses(entityType, entityId), "Addresses fetched successfully");
    }

    @GetMapping(value = "/address/{addressId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<AddressDto> getAddress(@PathVariable Long addressId) throws CommonException {
        log.info("Fetching address ID: {}", addressId);
        return ResponseResource.success(HttpStatus.OK, addressService.getAddress(addressId), "Address fetched successfully");
    }

    @PostMapping(value = "/address/{entityType}/{entityId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createAddress(
            @PathVariable EntityType entityType,
            @PathVariable Long entityId,
            @Valid @RequestBody AddressDto request) throws CommonException {
        log.info("Creating address for {}:{}", entityType, entityId);
        return ResponseResource.success(HttpStatus.CREATED, addressService.createAddress(entityType, entityId, request), "Address created successfully");
    }

    @PutMapping(value = "/address/{addressId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> updateAddress(
            @PathVariable Long addressId,
            @Valid @RequestBody AddressDto request) throws CommonException {
        log.info("Updating address ID: {}", addressId);
        return ResponseResource.success(HttpStatus.OK, addressService.updateAddress(addressId, request), "Address updated successfully");
    }

    @PatchMapping(value = "/address/{addressId}/primary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> setPrimaryAddress(@PathVariable Long addressId) throws CommonException {
        log.info("Setting address {} as primary", addressId);
        return ResponseResource.success(HttpStatus.OK, addressService.setPrimaryAddress(addressId), "Primary address updated successfully");
    }

    @DeleteMapping(value = "/address/{addressId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> deleteAddress(@PathVariable Long addressId) throws CommonException {
        log.info("Deleting address ID: {}", addressId);
        return ResponseResource.success(HttpStatus.OK, addressService.deleteAddress(addressId), "Address deleted successfully");
    }
}
