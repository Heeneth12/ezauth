# Auth / User / Tenant — API Audit & Error Handling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all critical bugs + error handling gaps in the auth/user/tenant packages, then implement 10 missing API endpoints.

**Architecture:** Two layers executed in order — Layer 1 (bugs + error hardening) must compile and be committed before Layer 2 (new endpoints) starts. All OTP state lives in Caffeine-backed Spring Cache (no DB schema changes). All new endpoints follow the existing `ResponseResource<T>` envelope and tenant-scoping via `UserContextUtil`.

**Tech Stack:** Spring Boot 4.0, Java 21, Caffeine Cache, Spring Security (JWT), Lombok, JPA/Hibernate, PostgreSQL

---

## File Map

**Modified (Layer 1):**
- `src/main/java/com/ezh/ezauth/config/CacheConfig.java` — add Caffeine CacheManager with per-cache TTL
- `src/main/java/com/ezh/ezauth/tenant/service/TenantService.java` — OTP fix, RuntimeException → CommonException, generateTenantCode fix
- `src/main/java/com/ezh/ezauth/auth/service/AuthService.java` — isActive check, RuntimeException → CommonException
- `src/main/java/com/ezh/ezauth/user/service/UserService.java` — RuntimeException → CommonException, searchUsers pagination
- `src/main/java/com/ezh/ezauth/auth/controller/AuthController.java` — initUser NPE fix, @Valid additions
- `src/main/java/com/ezh/ezauth/user/controller/UserController.java` — HTTP method fix, @Valid, searchUsers params
- `src/main/java/com/ezh/ezauth/tenant/controller/TenantController.java` — HTTP method fix
- `src/main/java/com/ezh/ezauth/utils/exception/GlobalExceptionHandler.java` — add 8 new handlers

**Created (Layer 2):**
- `src/main/java/com/ezh/ezauth/auth/dto/ForgotPasswordRequest.java`
- `src/main/java/com/ezh/ezauth/auth/dto/ResetPasswordRequest.java`
- `src/main/java/com/ezh/ezauth/auth/dto/ResendOtpRequest.java`

**Modified (Layer 2):**
- `src/main/java/com/ezh/ezauth/auth/service/AuthService.java` — add signout, forgotPassword, resetPassword
- `src/main/java/com/ezh/ezauth/auth/controller/AuthController.java` — add 4 new endpoints
- `src/main/java/com/ezh/ezauth/tenant/service/TenantService.java` — add resendOtp, toggleTenantStatus, deleteTenantAddress
- `src/main/java/com/ezh/ezauth/tenant/controller/TenantController.java` — add 3 new endpoints
- `src/main/java/com/ezh/ezauth/user/service/UserService.java` — add getUserAddresses, deleteUserAddress
- `src/main/java/com/ezh/ezauth/user/controller/UserController.java` — add 3 new endpoints
- `src/main/java/com/ezh/ezauth/config/SecurityConfig.java` — add new public endpoints to permitAll

---

## LAYER 1 — Bug Fixes & Error Hardening

---

### Task 1: Configure Caffeine CacheManager with per-cache TTL

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/config/CacheConfig.java`

The current `CacheConfig` is empty — it just has `@EnableCaching`. The existing caches (`userInitCache`, `userMiniCache`) were running on an auto-configured `ConcurrentMapCache` with no TTL. Caffeine is already in the pom.xml. We need to register all caches explicitly with proper TTLs so the new `otpCache` and `pwdResetCache` expire correctly.

- [ ] **Step 1: Replace CacheConfig.java with Caffeine-backed CacheManager**

Replace the entire file content:

```java
package com.ezh.ezauth.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("userInitCache",
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build());
        manager.registerCustomCache("userMiniCache",
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build());
        manager.registerCustomCache("otpCache",
                Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build());
        manager.registerCustomCache("pwdResetCache",
                Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).build());
        return manager;
    }
}
```

- [ ] **Step 2: Compile to verify no errors**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ezh/ezauth/config/CacheConfig.java
git commit -m "feat: configure Caffeine CacheManager with per-cache TTL for OTP and session caches"
```

---

### Task 2: Fix generateTenantCode + replace RuntimeException in TenantService (non-OTP)

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/tenant/service/TenantService.java`

Three independent fixes in TenantService:
1. `generateTenantCode` can throw `StringIndexOutOfBoundsException` when the sanitized string is shorter than the original name length.
2. Several methods throw bare `RuntimeException` which returns 500 with raw message.

- [ ] **Step 1: Fix generateTenantCode**

Find and replace this method at the bottom of TenantService:

```java
// OLD (line ~603):
private String generateTenantCode(String tenantName) {
    return tenantName.toUpperCase()
            .replaceAll("[^A-Z0-9]", "")
            .substring(0, Math.min(tenantName.length(), 6));
}
```

Replace with:

```java
private String generateTenantCode(String tenantName) {
    String sanitized = tenantName.toUpperCase().replaceAll("[^A-Z0-9]", "");
    if (sanitized.isEmpty()) sanitized = "TENANT";
    return sanitized.substring(0, Math.min(sanitized.length(), 6));
}
```

- [ ] **Step 2: Fix RuntimeException in registerTenant**

Find these two throws in `registerTenant`:

```java
// Line ~76:
throw new RuntimeException("Email already registered");

// Line ~86:
throw new RuntimeException("Invalid application");
```

Replace with:

```java
throw new CommonException("Email already registered", HttpStatus.CONFLICT);

throw new CommonException("Invalid application key", HttpStatus.BAD_REQUEST);
```

Also find the subscription plan throw in `registerTenant`:

```java
throw new RuntimeException("Default subscription plan not found");
```

Replace with:

```java
throw new CommonException("Default subscription plan not found. Contact support.", HttpStatus.INTERNAL_SERVER_ERROR);
```

- [ ] **Step 3: Fix RuntimeException in verifyTenantEmail, updateTenant, getTenantById, registerGoogleTenant**

Find and replace each:

```java
// verifyTenantEmail line ~239:
throw new RuntimeException("Tenant not found with ID: " + tenantId);
// → replace with:
throw new CommonException("Tenant not found", HttpStatus.NOT_FOUND);

// verifyTenantEmail line ~243:
throw new RuntimeException("User not found");
// → replace with:
throw new CommonException("User not found", HttpStatus.NOT_FOUND);

// updateTenant line ~290:
throw new RuntimeException("Tenant not found with ID: " + tenantId);
// → replace with:
throw new CommonException("Tenant not found", HttpStatus.NOT_FOUND);

// getTenantById line ~328:
throw new RuntimeException("Tenant not found with ID: " + tenantId);
// → replace with:
throw new CommonException("Tenant not found", HttpStatus.NOT_FOUND);

// registerGoogleTenant line ~367:
throw new RuntimeException("Invalid application key for registration");
// → replace with:
throw new CommonException("Invalid application key", HttpStatus.BAD_REQUEST);

// registerGoogleTenant subscription plan throw:
throw new RuntimeException("Default subscription plan not found");
// → replace with:
throw new CommonException("Default subscription plan not found. Contact support.", HttpStatus.INTERNAL_SERVER_ERROR);
```

Also fix `updateTenant` and `verifyTenantEmail` method signatures — they throw `CommonException` now but the method signatures declare no checked exception. Since `CommonException extends RuntimeException`, no signature change needed.

- [ ] **Step 4: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ezh/ezauth/tenant/service/TenantService.java
git commit -m "fix: replace RuntimeException with CommonException in TenantService, fix generateTenantCode index bug"
```

---

### Task 3: Fix AuthController.initUser NPE

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/auth/controller/AuthController.java`

`request.getHeader("Authorization")` returns `null` if the header is absent, causing a `NullPointerException` on `.replace()`. Spring Security's `JwtAuthFilter` won't always reject requests before they hit this endpoint (the filter allows requests through even without a valid token in some configurations).

- [ ] **Step 1: Replace the token extraction in initUser**

Find the current `initUser` method:

```java
@GetMapping(value = "/user/init", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<UserInitResponse> initUser(HttpServletRequest request) throws CommonException {
    String token = request.getHeader("Authorization").replace("Bearer ", "");
    UserInitResponse response = authService.initUser(token);
    return ResponseResource.success(HttpStatus.OK, response, "User init successful");
}
```

Replace with:

```java
@GetMapping(value = "/user/init", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<UserInitResponse> initUser(HttpServletRequest request) throws CommonException {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        throw new CommonException("Authorization header missing or malformed", HttpStatus.UNAUTHORIZED);
    }
    String token = authHeader.substring(7);
    UserInitResponse response = authService.initUser(token);
    return ResponseResource.success(HttpStatus.OK, response, "User init successful");
}
```

- [ ] **Step 2: Add missing @Valid on refreshToken**

Find:

```java
public ResponseResource<AuthResponse> refreshToken(@RequestBody TokenRefreshRequest request) throws CommonException {
```

Replace with:

```java
public ResponseResource<AuthResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) throws CommonException {
```

- [ ] **Step 3: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ezh/ezauth/auth/controller/AuthController.java
git commit -m "fix: null-safe Authorization header extraction in initUser, add @Valid on refreshToken"
```

---

### Task 4: Fix inactive user sign-in + RuntimeException in AuthService

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/auth/service/AuthService.java`

After Spring Security authenticates the user, the service fetches the User entity but never checks `isActive`. An admin who deactivates a user via `toggleUserStatus` will find that user can still sign in.

- [ ] **Step 1: Add isActive check in signIn**

Find in `signIn` (after `userRepository.findByEmail(...).orElseThrow(...)`):

```java
User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new RuntimeException("User not found"));

if (!subscriptionService.hasValidSubscription(...)) {
```

Replace with:

```java
User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new CommonException("Invalid credentials", HttpStatus.UNAUTHORIZED));

if (!user.getIsActive()) {
    throw new CommonException("Account is inactive. Contact your administrator.", HttpStatus.FORBIDDEN);
}

if (!subscriptionService.hasValidSubscription(...)) {
```

- [ ] **Step 2: Fix RuntimeException in refreshToken**

Find in `refreshToken`:

```java
if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
    throw new RuntimeException("Invalid refresh token");
}
```

Replace with:

```java
if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
    throw new CommonException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
}
```

Find:

```java
User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found"));
```

Replace with:

```java
User user = userRepository.findById(userId)
        .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));
```

Also fix the missing `tokenType` in `refreshToken` response. Find:

```java
return AuthResponse
        .builder()
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken)
        .message("")
        .build();
```

Replace with:

```java
return AuthResponse
        .builder()
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken)
        .tokenType("Bearer")
        .message("Token refreshed successfully")
        .build();
```

- [ ] **Step 3: Fix RuntimeException in getUserInitDetails (UserService)**

Open `src/main/java/com/ezh/ezauth/user/service/UserService.java`.

Find:

```java
User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

if (!user.getIsActive()) {
    throw new IllegalStateException("User account is inactive");
}
```

Replace with:

```java
User user = userRepository.findById(userId)
        .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

if (!user.getIsActive()) {
    throw new CommonException("Account is inactive. Contact your administrator.", HttpStatus.FORBIDDEN);
}
```

- [ ] **Step 4: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ezh/ezauth/auth/service/AuthService.java
git add src/main/java/com/ezh/ezauth/user/service/UserService.java
git commit -m "fix: block inactive user sign-in, replace RuntimeException with CommonException in AuthService and UserService"
```

---

### Task 5: Fix OTP validation — store on register, validate on verify

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/tenant/service/TenantService.java`

The OTP is generated and emailed in `registerTenant` but the variable is never stored anywhere. `verifyTenantEmail` accepts any OTP string and verifies the tenant unconditionally. This task: store OTP in `otpCache` on registration, validate it in `verifyTenantEmail`.

- [ ] **Step 1: Inject CacheManager into TenantService**

Find the field declarations at the top of `TenantService`:

```java
private final TenantDetailsRepository detailsRepository;
private final JwtTokenProvider jwtTokenProvider;
```

Add `CacheManager` after them:

```java
private final TenantDetailsRepository detailsRepository;
private final JwtTokenProvider jwtTokenProvider;
private final org.springframework.cache.CacheManager cacheManager;
```

Add the import at the top of the file:

```java
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
```

- [ ] **Step 2: Store OTP in cache after generating it in registerTenant**

Find in `registerTenant`:

```java
String otp = String.format("%06d", new Random().nextInt(999999));
emailService.sendOtpEmail(adminUser.getEmail(), otp);

userRoleRepository.save(userRole);
emailService.sendWelcomeEmail(request.getAdminEmail(), request.getTenantName());
```

Replace with:

```java
String otp = String.format("%06d", new Random().nextInt(999999));
Cache otpCacheRef = cacheManager.getCache("otpCache");
if (otpCacheRef != null) {
    otpCacheRef.put("otp:tenant:" + tenant.getId(), otp);
}
emailService.sendOtpEmail(adminUser.getEmail(), otp);

userRoleRepository.save(userRole);
emailService.sendWelcomeEmail(request.getAdminEmail(), request.getTenantName());
```

- [ ] **Step 3: Validate OTP in verifyTenantEmail**

Replace the entire `verifyTenantEmail` method:

```java
@Transactional
public AuthResponse verifyTenantEmail(Long tenantId, String otp) throws CommonException {

    Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

    if (Boolean.TRUE.equals(tenant.getIsVerify())) {
        throw new CommonException("Tenant already verified", HttpStatus.CONFLICT);
    }

    Cache otpCacheRef = cacheManager.getCache("otpCache");
    String cachedOtp = otpCacheRef != null
            ? otpCacheRef.get("otp:tenant:" + tenantId, String.class)
            : null;

    if (cachedOtp == null) {
        throw new CommonException("OTP has expired or was not found. Please request a new OTP.", HttpStatus.GONE);
    }

    if (!cachedOtp.equals(otp)) {
        throw new CommonException("Invalid OTP", HttpStatus.BAD_REQUEST);
    }

    if (otpCacheRef != null) {
        otpCacheRef.evict("otp:tenant:" + tenantId);
    }

    tenant.setIsVerify(true);
    tenantRepository.save(tenant);

    User user = userRepository.findByEmail(tenant.getTenantAdmin().getEmail())
            .orElseThrow(() -> new CommonException("Admin user not found", HttpStatus.NOT_FOUND));

    String roles = extractUserRoles(user);

    String accessToken = jwtTokenProvider.generateAccessToken(
            user.getId(),
            user.getEmail(),
            user.getTenant().getId(),
            user.getUserType().name(),
            roles
    );
    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

    return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .message("Email verified successfully")
            .build();
}
```

- [ ] **Step 4: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ezh/ezauth/tenant/service/TenantService.java
git commit -m "fix: store OTP in Caffeine cache on registration and validate it in verifyTenantEmail"
```

---

### Task 6: Fix HTTP method mismatches + add @Valid on UserController

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/user/controller/UserController.java`
- Modify: `src/main/java/com/ezh/ezauth/tenant/controller/TenantController.java`

`POST /{id}/update` should be `PUT /{id}` on both controllers. Missing `@Valid` on `createUser`.

- [ ] **Step 1: Fix UserController — rename endpoint and add @Valid**

Find:

```java
@PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> createUser(@RequestBody CreateUserRequest request) throws CommonException {
```

Replace with:

```java
@PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> createUser(@Valid @RequestBody CreateUserRequest request) throws CommonException {
```

Add the import if not present:

```java
import jakarta.validation.Valid;
```

Find:

```java
@PostMapping(value = "/{userId}/update", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> updateUser(@PathVariable Long userId, @RequestBody CreateUserRequest request) throws CommonException {
```

Replace with:

```java
@PutMapping(value = "/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> updateUser(@PathVariable Long userId, @Valid @RequestBody CreateUserRequest request) throws CommonException {
```

- [ ] **Step 2: Fix TenantController — rename update endpoint**

Find:

```java
@PostMapping(value = "/{tenantId}/update", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> updateTenant(@PathVariable Long tenantId, @RequestBody TenantRegistrationRequest request) throws CommonException {
```

Replace with:

```java
@PutMapping(value = "/{tenantId}", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> updateTenant(@PathVariable Long tenantId, @Valid @RequestBody TenantRegistrationRequest request) throws CommonException {
```

- [ ] **Step 3: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ezh/ezauth/user/controller/UserController.java
git add src/main/java/com/ezh/ezauth/tenant/controller/TenantController.java
git commit -m "fix: rename POST update endpoints to PUT, add @Valid on request bodies"
```

---

### Task 7: Fix searchUsers unbounded query

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/user/service/UserService.java`
- Modify: `src/main/java/com/ezh/ezauth/user/controller/UserController.java`

`searchUsers` uses `Pageable.unpaged()` which can return the entire users table. Fix by adding `page` + `size` params, matching the `getAllUsers` pattern.

- [ ] **Step 1: Update UserService.searchUsers signature**

Find:

```java
public Page<UserDto> searchUsers(UserFilter filter) {
    Pageable pageable = Pageable.unpaged();

    String email = ...
    ...

    return userRepository
            .findUsersWithAllFilters(tenantId, userId, userUuid, email, phone, search, userTypes, isActive, pageable)
            .map(dto -> constructUserDto(dto, true));
}
```

Replace with:

```java
public Page<UserDto> searchUsers(UserFilter filter, Integer page, Integer size) {
    int safePage = page != null ? page : 0;
    int safeSize = (size != null) ? Math.min(size, 500) : 50;
    Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("id").descending());

    String email = (filter != null && StringUtils.hasText(filter.getEmail())) ? filter.getEmail().trim().toLowerCase() : null;
    String search = (filter != null && StringUtils.hasText(filter.getSearchQuery())) ? filter.getSearchQuery().trim().toLowerCase() : null;
    Long tenantId = (filter != null) ? filter.getTenantId() : null;
    Long userId = (filter != null) ? filter.getUserId() : null;
    String userUuid = (filter != null) ? filter.getUserUuid() : null;
    String phone = (filter != null) ? filter.getPhone() : null;
    List<UserType> userTypes = (filter != null) ? filter.getUserType() : null;
    Boolean isActive = (filter != null) ? filter.getIsActive() : null;

    return userRepository
            .findUsersWithAllFilters(tenantId, userId, userUuid, email, phone, search, userTypes, isActive, pageable)
            .map(dto -> constructUserDto(dto, true));
}
```

- [ ] **Step 2: Update UserController.searchUsers to pass page and size**

Find:

```java
@PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<Page<UserDto>> searchUsers(@RequestBody UserFilter filter) throws CommonException {
    log.info("Entered get all users details");
    Page<UserDto> response = userService.searchUsers(filter);
    return ResponseResource.success(HttpStatus.OK, response, "All users fetched successfully");
}
```

Replace with:

```java
@PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<Page<UserDto>> searchUsers(
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "50") Integer size,
        @RequestBody UserFilter filter) throws CommonException {
    log.info("Entered search users");
    Page<UserDto> response = userService.searchUsers(filter, page, size);
    return ResponseResource.success(HttpStatus.OK, response, "Users fetched successfully");
}
```

- [ ] **Step 3: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ezh/ezauth/user/service/UserService.java
git add src/main/java/com/ezh/ezauth/user/controller/UserController.java
git commit -m "fix: add pagination to searchUsers to prevent unbounded query"
```

---

### Task 8: Harden GlobalExceptionHandler

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/utils/exception/GlobalExceptionHandler.java`

The current generic handler leaks `ex.getMessage()` which can expose SQL errors, class names, and stack trace snippets. Add 8 new handlers for common Spring exceptions and fix the generic handler.

- [ ] **Step 1: Replace the entire GlobalExceptionHandler**

```java
package com.ezh.ezauth.utils.exception;

import com.ezh.ezauth.utils.common.ResponseResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ResponseResource<?>> handleCommonException(CommonException ex) {
        log.error("Service exception: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(ex.getHttpStatus(), ex.getMessage()),
                ex.getHttpStatus()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseResource<?>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.error("Validation failed: {}", errorMessage);
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.BAD_REQUEST, errorMessage),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseResource<?>> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Illegal argument: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.BAD_REQUEST, ex.getMessage()),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ResponseResource<?>> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.CONFLICT, ex.getMessage()),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseResource<?>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.FORBIDDEN, "Access denied"),
                HttpStatus.FORBIDDEN
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseResource<?>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        log.error("Malformed request body: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.BAD_REQUEST, "Malformed or missing request body"),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseResource<?>> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        log.error("Missing request parameter: {}", message);
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.BAD_REQUEST, message),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseResource<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        log.error("Type mismatch: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.BAD_REQUEST, message),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseResource<?>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.CONFLICT, "A record with the same unique value already exists"),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ResponseResource<?>> handleNoHandler(NoHandlerFoundException ex) {
        String message = "No endpoint found for " + ex.getHttpMethod() + " " + ex.getRequestURL();
        log.warn(message);
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.NOT_FOUND, message),
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResource<?>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
```

- [ ] **Step 2: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ezh/ezauth/utils/exception/GlobalExceptionHandler.java
git commit -m "fix: harden GlobalExceptionHandler — add 8 new handlers, stop leaking internal messages"
```

---

## LAYER 2 — Missing APIs

---

### Task 9: Create request DTOs for new auth endpoints

**Files:**
- Create: `src/main/java/com/ezh/ezauth/auth/dto/ForgotPasswordRequest.java`
- Create: `src/main/java/com/ezh/ezauth/auth/dto/ResetPasswordRequest.java`
- Create: `src/main/java/com/ezh/ezauth/auth/dto/ResendOtpRequest.java`

- [ ] **Step 1: Create ForgotPasswordRequest.java**

```java
package com.ezh.ezauth.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}
```

- [ ] **Step 2: Create ResetPasswordRequest.java**

```java
package com.ezh.ezauth.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "OTP is required")
    private String otp;

    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String newPassword;
}
```

- [ ] **Step 3: Create ResendOtpRequest.java**

```java
package com.ezh.ezauth.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResendOtpRequest {

    @NotNull(message = "Tenant ID is required")
    private Long tenantId;
}
```

- [ ] **Step 4: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ezh/ezauth/auth/dto/ForgotPasswordRequest.java
git add src/main/java/com/ezh/ezauth/auth/dto/ResetPasswordRequest.java
git add src/main/java/com/ezh/ezauth/auth/dto/ResendOtpRequest.java
git commit -m "feat: add request DTOs for forgot-password, reset-password, resend-otp"
```

---

### Task 10: Add signout to AuthService + AuthController

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/auth/service/AuthService.java`
- Modify: `src/main/java/com/ezh/ezauth/auth/controller/AuthController.java`

Signout evicts the `userInitCache` entry for the user. JWTs are stateless so the token is still technically valid until expiry, but the cache eviction forces a fresh DB load on the next `/user/init` call, and any downstream system checking `/validate` will still accept the token — this is the agreed lightweight signout approach.

- [ ] **Step 1: Inject CacheManager and PasswordEncoder into AuthService**

Find the field declarations at the top of `AuthService`:

```java
private final UserRepository userRepository;
private final UserService userService;
private final JwtTokenProvider jwtTokenProvider;
private final AuthenticationManager authenticationManager;
private final TenantService tenantService;
private final SubscriptionService subscriptionService;
```

Add two new fields:

```java
private final UserRepository userRepository;
private final UserService userService;
private final JwtTokenProvider jwtTokenProvider;
private final AuthenticationManager authenticationManager;
private final TenantService tenantService;
private final SubscriptionService subscriptionService;
private final org.springframework.cache.CacheManager cacheManager;
private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
```

Add imports at the top:

```java
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
```

- [ ] **Step 2: Add signout method to AuthService**

Add this method after `validateToken`:

```java
public CommonResponse signout(String token) {
    try {
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        Cache userInitCacheRef = cacheManager.getCache("userInitCache");
        if (userInitCacheRef != null) {
            userInitCacheRef.evict(userId);
        }
    } catch (Exception e) {
        log.warn("Signout cache eviction skipped: {}", e.getMessage());
    }
    return CommonResponse.builder()
            .status(Status.SUCCESS)
            .message("Signed out successfully")
            .build();
}
```

- [ ] **Step 3: Add signout endpoint to AuthController**

Add after the `validateToken` endpoint:

```java
@PostMapping(value = "/signout", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> signout(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) throws CommonException {
    log.info("Entered signout");
    String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;
    CommonResponse response = authService.signout(token);
    return ResponseResource.success(HttpStatus.OK, response, "Signed out successfully");
}
```

- [ ] **Step 4: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ezh/ezauth/auth/service/AuthService.java
git add src/main/java/com/ezh/ezauth/auth/controller/AuthController.java
git commit -m "feat: add POST /auth/signout endpoint with cache eviction"
```

---

### Task 11: Add forgot-password + reset-password to AuthService + AuthController

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/auth/service/AuthService.java`
- Modify: `src/main/java/com/ezh/ezauth/auth/controller/AuthController.java`

- [ ] **Step 1: Add forgotPassword method to AuthService**

Add after the `signout` method:

```java
@Transactional(readOnly = true)
public CommonResponse forgotPassword(ForgotPasswordRequest request) {
    userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
        String otp = String.format("%06d", new Random().nextInt(999999));
        Cache pwdResetCache = cacheManager.getCache("pwdResetCache");
        if (pwdResetCache != null) {
            pwdResetCache.put(request.getEmail(), otp);
        }
        emailService.sendOtpEmail(request.getEmail(), otp);
    });
    return CommonResponse.builder()
            .status(Status.SUCCESS)
            .message("If this email exists, a reset code has been sent")
            .build();
}
```

Add the missing imports at the top of `AuthService`:

```java
import com.ezh.ezauth.auth.dto.ForgotPasswordRequest;
import com.ezh.ezauth.auth.dto.ResetPasswordRequest;
import com.ezh.ezauth.utils.EmailService;
import java.util.Random;
```

Also inject `EmailService` into `AuthService` fields (it's not currently there):

```java
private final org.springframework.cache.CacheManager cacheManager;
private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
private final com.ezh.ezauth.utils.EmailService emailService;
```

- [ ] **Step 2: Add resetPassword method to AuthService**

Add after `forgotPassword`:

```java
@Transactional
public CommonResponse resetPassword(ResetPasswordRequest request) throws CommonException {
    Cache pwdResetCache = cacheManager.getCache("pwdResetCache");
    String cachedOtp = pwdResetCache != null
            ? pwdResetCache.get(request.getEmail(), String.class)
            : null;

    if (cachedOtp == null) {
        throw new CommonException("OTP has expired. Please request a new password reset.", HttpStatus.GONE);
    }

    if (!cachedOtp.equals(request.getOtp())) {
        throw new CommonException("Invalid OTP", HttpStatus.BAD_REQUEST);
    }

    User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);

    if (pwdResetCache != null) pwdResetCache.evict(request.getEmail());
    Cache userInitCacheRef = cacheManager.getCache("userInitCache");
    if (userInitCacheRef != null) userInitCacheRef.evict(user.getId());

    return CommonResponse.builder()
            .status(Status.SUCCESS)
            .message("Password reset successfully")
            .build();
}
```

- [ ] **Step 3: Add forgot-password + reset-password endpoints to AuthController**

Add these two endpoints after the `signout` endpoint:

```java
@PostMapping(value = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    log.info("Entered forgot-password for email: {}", request.getEmail());
    CommonResponse response = authService.forgotPassword(request);
    return ResponseResource.success(HttpStatus.OK, response, response.getMessage());
}

@PostMapping(value = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) throws CommonException {
    log.info("Entered reset-password");
    CommonResponse response = authService.resetPassword(request);
    return ResponseResource.success(HttpStatus.OK, response, "Password reset successfully");
}
```

Add the import in `AuthController`:

```java
import com.ezh.ezauth.auth.dto.ForgotPasswordRequest;
import com.ezh.ezauth.auth.dto.ResetPasswordRequest;
```

- [ ] **Step 4: Add forgot-password and reset-password to SecurityConfig permitAll**

Open `SecurityConfig.java`. Find:

```java
.requestMatchers(
        "/api/v1/auth/register",
        "/api/v1/auth/signin",
        "/api/v1/auth/refresh",
        "/api/v1/auth/google",
        "/api/v1/auth/verifyTenant/**",
        "/api/v1/auth/activate/**",
        "/actuator/**"
).permitAll()
```

Replace with:

```java
.requestMatchers(
        "/api/v1/auth/register",
        "/api/v1/auth/signin",
        "/api/v1/auth/refresh",
        "/api/v1/auth/google",
        "/api/v1/auth/verifyTenant/**",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
        "/api/v1/auth/resend-otp",
        "/actuator/**"
).permitAll()
```

Note: removed the dead `/api/v1/auth/activate/**` route (no endpoint exists for it).

- [ ] **Step 5: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ezh/ezauth/auth/service/AuthService.java
git add src/main/java/com/ezh/ezauth/auth/controller/AuthController.java
git add src/main/java/com/ezh/ezauth/config/SecurityConfig.java
git commit -m "feat: add POST /auth/forgot-password and POST /auth/reset-password endpoints"
```

---

### Task 12: Add resend-otp to TenantService + AuthController

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/tenant/service/TenantService.java`
- Modify: `src/main/java/com/ezh/ezauth/auth/controller/AuthController.java`

- [ ] **Step 1: Add resendOtp method to TenantService**

Add after the `verifyTenantEmail` method:

```java
@Transactional(readOnly = true)
public CommonResponse resendOtp(Long tenantId) throws CommonException {
    Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

    if (Boolean.TRUE.equals(tenant.getIsVerify())) {
        throw new CommonException("Tenant is already verified", HttpStatus.CONFLICT);
    }

    User admin = userRepository.findByEmail(tenant.getTenantAdmin().getEmail())
            .orElseThrow(() -> new CommonException("Admin user not found", HttpStatus.NOT_FOUND));

    String otp = String.format("%06d", new Random().nextInt(999999));
    Cache otpCacheRef = cacheManager.getCache("otpCache");
    if (otpCacheRef != null) {
        otpCacheRef.put("otp:tenant:" + tenantId, otp);
    }
    emailService.sendOtpEmail(admin.getEmail(), otp);

    return CommonResponse.builder()
            .status(Status.SUCCESS)
            .message("OTP resent successfully")
            .build();
}
```

- [ ] **Step 2: Add resend-otp endpoint to AuthController**

Add after the `resetPassword` endpoint:

```java
@PostMapping(value = "/resend-otp", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) throws CommonException {
    log.info("Resending OTP for tenantId: {}", request.getTenantId());
    CommonResponse response = tenantService.resendOtp(request.getTenantId());
    return ResponseResource.success(HttpStatus.OK, response, "OTP resent successfully");
}
```

Add the import in `AuthController`:

```java
import com.ezh.ezauth.auth.dto.ResendOtpRequest;
```

- [ ] **Step 3: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ezh/ezauth/tenant/service/TenantService.java
git add src/main/java/com/ezh/ezauth/auth/controller/AuthController.java
git commit -m "feat: add POST /auth/resend-otp endpoint"
```

---

### Task 13: Add GET /me + address endpoints to UserService + UserController

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/user/service/UserService.java`
- Modify: `src/main/java/com/ezh/ezauth/user/controller/UserController.java`

- [ ] **Step 1: Add deleteUserAddress to UserService**

Add after the `updateUserAddress` method:

```java
@Transactional
@CacheEvict(value = "userInitCache", key = "#userId")
public CommonResponse deleteUserAddress(Long userId, Long addressId) throws CommonException {
    Long tenantId = UserContextUtil.getTenantId();
    User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
            .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

    if (user.getAddresses() == null || user.getAddresses().isEmpty()) {
        throw new CommonException("No addresses found for this user", HttpStatus.NOT_FOUND);
    }

    UserAddress addressToDelete = user.getAddresses().stream()
            .filter(a -> a.getId() != null && a.getId().equals(addressId))
            .findFirst()
            .orElseThrow(() -> new CommonException("Address not found", HttpStatus.NOT_FOUND));

    user.getAddresses().remove(addressToDelete);
    userRepository.save(user);

    return CommonResponse.builder()
            .id(userId.toString())
            .message("Address deleted successfully")
            .status(Status.SUCCESS)
            .build();
}
```

Add a method to get user addresses:

```java
@Transactional(readOnly = true)
public Set<UserAddressDto> getUserAddresses(Long userId) throws CommonException {
    Long tenantId = UserContextUtil.getTenantId();
    User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
            .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

    if (user.getAddresses() == null) {
        return Collections.emptySet();
    }

    return user.getAddresses().stream()
            .map(this::mapToAddressDto)
            .collect(Collectors.toSet());
}
```

Add the import for `Set` and `Collections` if not already present (they should be from existing imports).

- [ ] **Step 2: Add GET /me, GET /{userId}/addresses, DELETE /{userId}/address/{addressId} to UserController**

Add these three endpoints after the existing `updateUserAddress` endpoint:

```java
@GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<UserDto> getCurrentUser() throws CommonException {
    log.info("Fetching current user profile");
    Long userId = com.ezh.ezauth.utils.UserContextUtil.getUserIdOrThrow();
    UserDto response = userService.getUserById(userId, true);
    return ResponseResource.success(HttpStatus.OK, response, "Current user fetched successfully");
}

@GetMapping(value = "/{userId}/addresses", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<java.util.Set<UserAddressDto>> getUserAddresses(@PathVariable Long userId) throws CommonException {
    log.info("Fetching addresses for user ID: {}", userId);
    java.util.Set<UserAddressDto> response = userService.getUserAddresses(userId);
    return ResponseResource.success(HttpStatus.OK, response, "User addresses fetched successfully");
}

@DeleteMapping(value = "/{userId}/address/{addressId}", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> deleteUserAddress(
        @PathVariable Long userId,
        @PathVariable Long addressId) throws CommonException {
    log.info("Deleting address {} for user ID: {}", addressId, userId);
    CommonResponse response = userService.deleteUserAddress(userId, addressId);
    return ResponseResource.success(HttpStatus.OK, response, "Address deleted successfully");
}
```

- [ ] **Step 3: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ezh/ezauth/user/service/UserService.java
git add src/main/java/com/ezh/ezauth/user/controller/UserController.java
git commit -m "feat: add GET /user/me, GET /user/{id}/addresses, DELETE /user/{id}/address/{addressId}"
```

---

### Task 14: Add GET /current + toggle-status + delete address to TenantService + TenantController

**Files:**
- Modify: `src/main/java/com/ezh/ezauth/tenant/service/TenantService.java`
- Modify: `src/main/java/com/ezh/ezauth/tenant/controller/TenantController.java`

- [ ] **Step 1: Add toggleTenantStatus to TenantService**

Add after `resendOtp`:

```java
@Transactional
public CommonResponse toggleTenantStatus(Long tenantId) throws CommonException {
    Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

    boolean deactivating = Boolean.TRUE.equals(tenant.getIsActive());
    if (deactivating) {
        long activeUserCount = userRepository.countByTenant_IdAndIsActive(tenantId, true);
        if (activeUserCount > 0) {
            throw new CommonException(
                    "Cannot deactivate tenant with " + activeUserCount + " active user(s). Deactivate all users first.",
                    HttpStatus.CONFLICT);
        }
    }

    tenant.setIsActive(!tenant.getIsActive());
    tenantRepository.save(tenant);

    String statusLabel = Boolean.TRUE.equals(tenant.getIsActive()) ? "Active" : "Inactive";
    return CommonResponse.builder()
            .id(tenantId.toString())
            .status(Status.SUCCESS)
            .message("Tenant status toggled. Current status: " + statusLabel)
            .build();
}
```

- [ ] **Step 2: Add countByTenantIdAndIsActive to UserRepository**

Open `src/main/java/com/ezh/ezauth/user/repository/UserRepository.java` and add:

```java
long countByTenant_IdAndIsActive(Long tenantId, Boolean isActive);
```

- [ ] **Step 3: Add deleteTenantAddress to TenantService**

Add after `toggleTenantStatus`:

```java
@Transactional
public CommonResponse deleteTenantAddress(Long tenantId, Long addressId) throws CommonException {
    Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

    if (tenant.getAddresses() == null || tenant.getAddresses().isEmpty()) {
        throw new CommonException("No addresses found for this tenant", HttpStatus.NOT_FOUND);
    }

    TenantAddress addressToDelete = tenant.getAddresses().stream()
            .filter(a -> a.getId() != null && a.getId().equals(addressId))
            .findFirst()
            .orElseThrow(() -> new CommonException("Address not found for this tenant", HttpStatus.NOT_FOUND));

    tenant.getAddresses().remove(addressToDelete);
    tenantRepository.save(tenant);

    return CommonResponse.builder()
            .id(addressId.toString())
            .status(Status.SUCCESS)
            .message("Tenant address deleted successfully")
            .build();
}
```

- [ ] **Step 4: Add GET /current, PUT /{tenantId}/toggle-status, DELETE /{tenantId}/address/{addressId} to TenantController**

Add these three endpoints after the existing `updateTenantAddress` endpoint:

```java
@GetMapping(value = "/current", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<TenantDto> getCurrentTenant() throws CommonException {
    log.info("Fetching current tenant");
    Long tenantId = com.ezh.ezauth.utils.UserContextUtil.getTenantIdOrThrow();
    TenantDto response = tenantService.getTenantById(tenantId);
    return ResponseResource.success(HttpStatus.OK, response, "Current tenant fetched successfully");
}

@PutMapping(value = "/{tenantId}/toggle-status", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> toggleTenantStatus(@PathVariable Long tenantId) throws CommonException {
    log.info("Toggling status for tenant ID: {}", tenantId);
    CommonResponse response = tenantService.toggleTenantStatus(tenantId);
    return ResponseResource.success(HttpStatus.OK, response, response.getMessage());
}

@DeleteMapping(value = "/{tenantId}/address/{addressId}", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseResource<CommonResponse> deleteTenantAddress(
        @PathVariable Long tenantId,
        @PathVariable Long addressId) throws CommonException {
    log.info("Deleting address {} for tenant ID: {}", addressId, tenantId);
    CommonResponse response = tenantService.deleteTenantAddress(tenantId, addressId);
    return ResponseResource.success(HttpStatus.OK, response, "Tenant address deleted successfully");
}
```

- [ ] **Step 5: Compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ezh/ezauth/tenant/service/TenantService.java
git add src/main/java/com/ezh/ezauth/tenant/controller/TenantController.java
git add src/main/java/com/ezh/ezauth/user/repository/UserRepository.java
git commit -m "feat: add GET /tenant/current, PUT /tenant/{id}/toggle-status, DELETE /tenant/{id}/address/{addressId}"
```

---

## Final Verification

- [ ] **Full compile**

```bash
mvn clean compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Verify all 18 endpoints exist**

```bash
# Auth endpoints (10 total: 6 existing + 4 new)
# POST /api/v1/auth/register
# POST /api/v1/auth/signin
# GET  /api/v1/auth/user/init
# POST /api/v1/auth/refresh
# POST /api/v1/auth/google
# GET  /api/v1/auth/validate
# POST /api/v1/auth/verifyTenant
# POST /api/v1/auth/signout         ← NEW
# POST /api/v1/auth/forgot-password ← NEW
# POST /api/v1/auth/reset-password  ← NEW
# POST /api/v1/auth/resend-otp      ← NEW

# User endpoints (10 total: 7 existing + 3 new)
# POST /api/v1/user/create
# POST /api/v1/user/all
# GET  /api/v1/user/bulk
# GET  /api/v1/user/{userId}
# PUT  /api/v1/user/{userId}               ← renamed from POST /{userId}/update
# PUT  /api/v1/user/{userId}/toggle-status
# POST /api/v1/user/search
# POST /api/v1/user/{userId}/address
# PUT  /api/v1/user/{userId}/address/{addressId}
# GET  /api/v1/user/me                         ← NEW
# GET  /api/v1/user/{userId}/addresses         ← NEW
# DELETE /api/v1/user/{userId}/address/{addressId} ← NEW

# Tenant endpoints (9 total: 6 existing + 3 new)
# POST /api/v1/tenant/all
# GET  /api/v1/tenant/bulk
# PUT  /api/v1/tenant/{tenantId}               ← renamed from POST /{tenantId}/update
# GET  /api/v1/tenant/{tenantId}
# POST /api/v1/tenant/{tenantId}/details
# PUT  /api/v1/tenant/{tenantId}/details
# GET  /api/v1/tenant/{tenantId}/details
# POST /api/v1/tenant/{tenantId}/address
# PUT  /api/v1/tenant/{tenantId}/address/{addressId}
# GET  /api/v1/tenant/current                             ← NEW
# PUT  /api/v1/tenant/{tenantId}/toggle-status            ← NEW
# DELETE /api/v1/tenant/{tenantId}/address/{addressId}    ← NEW
```

- [ ] **Final commit if any loose files**

```bash
git status
# If clean: nothing to commit
```
