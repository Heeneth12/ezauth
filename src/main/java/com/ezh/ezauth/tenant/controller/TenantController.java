package com.ezh.ezauth.tenant.controller;

import com.ezh.ezauth.tenant.dto.*;
import com.ezh.ezauth.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse> signIn(
            @Valid @RequestBody TenantSignInRequest request) {

        try {
            TenantSignInResponse response = tenantService.signIn(request);

            return ResponseEntity.ok(
                    ApiResponse.builder()
                            .success(true)
                            .message("Sign in successful")
                            .data(response)
                            .build()
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .data(null)
                            .build()
            );
        }
    }
}