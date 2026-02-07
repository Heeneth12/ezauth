package com.ezh.ezauth.common.controller;


import com.ezh.ezauth.common.dto.ApplicationDto;
import com.ezh.ezauth.common.dto.RoleDto;
import com.ezh.ezauth.common.service.CommonService;
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
@RequestMapping("/api/v1/common")
@RequiredArgsConstructor
public class CommonController {

    private final CommonService commonService;

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

    @PostMapping(value = "/role/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createRole(@RequestBody RoleDto roleDto) throws CommonException {
        log.info("Entered create role");
        CommonResponse response = commonService.createRole(roleDto);
        return ResponseResource.success(HttpStatus.CREATED, response, "Role created successfully");
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
}
