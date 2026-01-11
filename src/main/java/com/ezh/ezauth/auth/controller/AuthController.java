package com.ezh.ezauth.auth.controller;


import com.ezh.ezauth.auth.dto.AuthResponse;
import com.ezh.ezauth.auth.dto.GoogleSignInRequest;
import com.ezh.ezauth.auth.dto.SignInRequest;
import com.ezh.ezauth.auth.dto.TokenRefreshRequest;
import com.ezh.ezauth.auth.service.AuthService;
import com.ezh.ezauth.tenant.dto.*;
import com.ezh.ezauth.tenant.service.TenantService;
import com.ezh.ezauth.user.dto.UserInitResponse;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.ResponseResource;
import com.ezh.ezauth.utils.exception.CommonException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TenantService tenantService;
    private final AuthService authService;


    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<TenantRegistrationResponse> registerTenant(@Valid @RequestBody TenantRegistrationRequest request) throws CommonException {
        log.info("Entered register tenant with : {}", request);
        TenantRegistrationResponse response = tenantService.registerTenant(request);
        return ResponseResource.success(HttpStatus.CREATED, response, "Tenant registered successfully");
    }

    @PostMapping(value = "/signin", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<AuthResponse> signIn(@Valid @RequestBody SignInRequest request) throws CommonException {
        log.info("Entered signin with : {}", request);
        AuthResponse response = authService.signIn(request);
        return ResponseResource.success(HttpStatus.OK, response, "Sign in successful");
    }

    @GetMapping(value = "/user/init", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<UserInitResponse> initUser(HttpServletRequest request) throws CommonException {
        String token = request.getHeader("Authorization").replace("Bearer ", "");
        UserInitResponse response = authService.initUser(token);
        return ResponseResource.success(HttpStatus.OK, response, "User init successful");
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<AuthResponse> refreshToken(@RequestBody TokenRefreshRequest request) throws CommonException {
        log.info("Entered refresh token with : {}", request);
        AuthResponse response = authService.refreshToken(request);
        return ResponseResource.success(HttpStatus.OK, response, "Token refreshed successfully");
    }

    @PostMapping(value = "/google", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<AuthResponse> signInWithGoogle(@Valid @RequestBody GoogleSignInRequest request) throws CommonException {
        log.info("Entered Google Sign-In");
        AuthResponse response = authService.signInWithGoogle(request);
        return ResponseResource.success(HttpStatus.OK, response, "Google Sign-In successful");
    }

    @GetMapping(value = "/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> validateToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) throws CommonException {
        log.info("Entered validateToken check");
        String token = bearerToken;
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            token = bearerToken.substring(7);
        }
        CommonResponse response = authService.validateToken(token);
        return ResponseResource.success(HttpStatus.OK, response, "Token is valid");
    }
}