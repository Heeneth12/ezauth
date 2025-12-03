package com.ezh.ezauth.common.controller;


import com.ezh.ezauth.common.dto.ApplicationDto;
import com.ezh.ezauth.common.dto.RoleDto;
import com.ezh.ezauth.common.service.CommonService;
import com.ezh.ezauth.user.dto.UserDto;
import com.ezh.ezauth.utils.common.ResponseResource;
import com.ezh.ezauth.utils.exception.CommonException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.Getter;
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
    public ResponseResource<List<ApplicationDto>> getAllApplication(HttpServletRequest request) throws CommonException {
        log.info("Entered get all applications");
        String token = request.getHeader("Authorization").replace("Bearer ", "");
        List<ApplicationDto> response = commonService.getAllApplications(token);
        return ResponseResource.success(HttpStatus.CREATED, response, "Tenant registered successfully");
    }

    @GetMapping(value = "/role/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<RoleDto>> getAllRoles(HttpServletRequest request) throws CommonException {
        log.info("Entered get all roles");
        List<RoleDto> response = commonService.getAllRoles();
        return ResponseResource.success(HttpStatus.CREATED, response, "User roles feched  successfully");
    }

    @GetMapping(value = "/apps/{appId}/modules", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<?> getModulesByApplication(@PathVariable Long appId) throws CommonException{
        log.info("Fetching modules for appId: {}", appId);
        Object response = commonService.getModulesByApplication(appId);
        return ResponseResource.success(HttpStatus.OK, response, "Modules fetched successfully");
    }

    @GetMapping("/modules/{moduleId}/privileges")
    public ResponseResource<?> getPrivilegesByModule(@PathVariable Long moduleId) throws CommonException{
        log.info("Fetching privileges for moduleId: {}", moduleId);
        Object response = new Object();
        return ResponseResource.success(HttpStatus.OK, response, "Privileges fetched successfully");
    }


}
