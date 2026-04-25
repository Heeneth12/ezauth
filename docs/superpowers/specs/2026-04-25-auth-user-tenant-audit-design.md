# Auth / User / Tenant — API Audit & Error Handling Design

**Date:** 2026-04-25
**Scope:** `auth`, `user`, `tenant` packages only
**Approach:** Two-layer — (1) fix existing bugs + harden error handling, (2) implement missing APIs

---

## 1. Bug Fixes (Layer 1)

### 1.1 Critical — OTP Never Validated

**File:** `TenantService.registerTenant` + `TenantService.verifyTenantEmail`

The OTP is generated and emailed during registration but never persisted. `verifyTenantEmail` accepts any OTP value and marks the tenant as verified without checking it.

**Fix:**
- On `registerTenant`: store OTP in Spring Cache under key `"otp:tenant:{tenantId}"` with a 10-minute TTL immediately after generation.
- On `verifyTenantEmail`:
  1. Fetch OTP from cache. If absent → `CommonException("OTP expired or not found", 410 GONE)`.
  2. Compare with submitted value. If mismatch → `CommonException("Invalid OTP", 400 BAD_REQUEST)`.
  3. If `tenant.getIsVerify() == true` → `CommonException("Tenant already verified", 409 CONFLICT)`.
  4. On success → evict cache entry, set `isVerify = true`, save, generate tokens.

**Cache key pattern:** `"otp:tenant:{tenantId}"` — stored as `String`.
**TTL:** Declare a dedicated cache name `"otpCache"` in `CacheConfig` with a 10-minute TTL. Use `cacheManager.getCache("otpCache")` manually (not `@Cacheable`) to allow explicit `put` and `evict` calls within the same transaction.

---

### 1.2 NullPointerException in `AuthController.initUser`

**File:** `AuthController.java` line 50

```java
// BUG: throws NPE if Authorization header is absent
String token = request.getHeader("Authorization").replace("Bearer ", "");
```

**Fix:**
```java
String authHeader = request.getHeader("Authorization");
if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    throw new CommonException("Authorization header missing or malformed", HttpStatus.UNAUTHORIZED);
}
String token = authHeader.substring(7);
```

---

### 1.3 Inactive User Can Sign In

**File:** `AuthService.signIn` — after fetching `user` from repository, no `isActive` check exists.

**Fix:** After `userRepository.findByEmail(...)`:
```java
if (!user.getIsActive()) {
    throw new CommonException("Account is inactive. Contact your administrator.", HttpStatus.FORBIDDEN);
}
```

---

### 1.4 `searchUsers` Unbounded Query

**File:** `UserService.searchUsers` line 784

Uses `Pageable.unpaged()` — can return the entire users table with no limit.

**Fix:** Add `page` and `size` parameters to `UserController.searchUsers` and `UserService.searchUsers`, aligned with `getAllUsers`. Default: `page=0`, `size=50`, max enforced at `500`.

---

### 1.5 `generateTenantCode` Index Bug

**File:** `TenantService.generateTenantCode` line 603

```java
// BUG: uses tenantName.length() (original) instead of sanitized string length
return tenantName.toUpperCase()
    .replaceAll("[^A-Z0-9]", "")
    .substring(0, Math.min(tenantName.length(), 6));
```

**Fix:**
```java
String sanitized = tenantName.toUpperCase().replaceAll("[^A-Z0-9]", "");
if (sanitized.isEmpty()) sanitized = "TENANT";
return sanitized.substring(0, Math.min(sanitized.length(), 6));
```

---

### 1.6 HTTP Method Mismatches

| Controller | Current | Correct |
|---|---|---|
| `UserController.updateUser` | `POST /{userId}/update` | `PUT /{userId}` |
| `TenantController.updateTenant` | `POST /{tenantId}/update` | `PUT /{tenantId}` |

---

## 2. Error Handling Hardening (Layer 1)

### 2.1 Replace `RuntimeException` in Services

All `RuntimeException` and `IllegalStateException` throws in `AuthService`, `TenantService`, and `UserService` must be replaced with `CommonException` carrying the appropriate `HttpStatus`.

| Location | Message | HttpStatus |
|---|---|---|
| `TenantService.registerTenant` — email exists | `"Email already registered"` | `409 CONFLICT` |
| `TenantService.registerTenant` — invalid appKey | `"Invalid application key"` | `400 BAD_REQUEST` |
| `TenantService.registerTenant` — no free trial plan | `"Default subscription plan not found"` | `500 INTERNAL_SERVER_ERROR` |
| `TenantService.verifyTenantEmail` — tenant not found | `"Tenant not found"` | `404 NOT_FOUND` |
| `TenantService.updateTenant` — tenant not found | `"Tenant not found"` | `404 NOT_FOUND` |
| `TenantService.getTenantById` — tenant not found | `"Tenant not found"` | `404 NOT_FOUND` |
| `TenantService.registerGoogleTenant` — invalid appKey | `"Invalid application key"` | `400 BAD_REQUEST` |
| `AuthService.signIn` — user not found | `"Invalid credentials"` | `401 UNAUTHORIZED` |
| `AuthService.refreshToken` — invalid token | `"Invalid or expired refresh token"` | `401 UNAUTHORIZED` |
| `AuthService.refreshToken` — user not found | `"User not found"` | `404 NOT_FOUND` |
| `UserService.getUserInitDetails` — user not found | `"User not found"` | `404 NOT_FOUND` |
| `UserService.getUserInitDetails` — inactive user | `"Account is inactive"` | `403 FORBIDDEN` |

### 2.2 Harden `GlobalExceptionHandler`

Add the following handlers to `GlobalExceptionHandler.java`:

| Exception | HTTP Status | Response message |
|---|---|---|
| `IllegalArgumentException` | `400 BAD_REQUEST` | `ex.getMessage()` |
| `IllegalStateException` | `409 CONFLICT` | `ex.getMessage()` |
| `AccessDeniedException` | `403 FORBIDDEN` | `"Access denied"` |
| `HttpMessageNotReadableException` | `400 BAD_REQUEST` | `"Malformed or missing request body"` |
| `MissingServletRequestParameterException` | `400 BAD_REQUEST` | `"Required parameter '{name}' is missing"` |
| `MethodArgumentTypeMismatchException` | `400 BAD_REQUEST` | `"Invalid value for parameter '{name}'"` |
| `DataIntegrityViolationException` | `409 CONFLICT` | `"A record with the same unique value already exists"` |
| `NoHandlerFoundException` | `404 NOT_FOUND` | `"No endpoint found for {method} {path}"` |
| Generic `Exception` (existing) | `500` | **Change to:** `"An unexpected error occurred"` — stop leaking `ex.getMessage()`. Log full exception internally. |

---

## 3. Missing APIs (Layer 2)

### 3.1 Auth — `/api/v1/auth`

#### `POST /signout`
- **Auth:** Required (Bearer token)
- **Request:** none
- **Behavior:** Extract `userId` from token, evict `userInitCache` for that userId.
- **Response:** `200 { message: "Signed out successfully" }`

#### `POST /forgot-password`
- **Auth:** None (public)
- **Request:** `{ "email": "string" }`
- **Behavior:** Look up user by email. If found, generate 6-digit OTP, store in `"pwdResetCache"` (declared in `CacheConfig` with 15-minute TTL) under key `email`, send via `EmailService.sendOtpEmail`. Always return 200 — never reveal whether the email exists.
- **Response:** `200 { message: "If this email exists, a reset code has been sent" }`

#### `POST /reset-password`
- **Auth:** None (public)
- **Request:** `{ "email": "string", "otp": "string", "newPassword": "string" }`
- **Behavior:** Fetch OTP from cache `"pwd-reset:{email}"`. If absent → `410 GONE`. If mismatch → `400 BAD_REQUEST`. On success → encode `newPassword`, update `user.passwordHash`, evict cache, evict `userInitCache` for userId.
- **Response:** `200 { message: "Password reset successfully" }`

#### `POST /resend-otp`
- **Auth:** None (public)
- **Request:** `{ "tenantId": long }`
- **Behavior:** Fetch tenant. If not found → `404`. If already verified → `409`. Regenerate OTP, overwrite cache `"otp:tenant:{tenantId}"` with fresh 10-minute TTL, resend to tenant admin email.
- **Response:** `200 { message: "OTP resent successfully" }`

---

### 3.2 User — `/api/v1/user`

#### `GET /me`
- **Auth:** Required
- **Behavior:** Extract `userId` from JWT via `UserContextUtil.getUserId()`, delegate to `userService.getUserById(userId, true)`.
- **Response:** `200 UserDto`

#### `GET /{userId}/addresses`
- **Auth:** Required
- **Behavior:** Fetch user by `userId` scoped to current tenant. Return `Set<UserAddressDto>` from `user.getAddresses()`.
- **Response:** `200 Set<UserAddressDto>`

#### `DELETE /{userId}/address/{addressId}`
- **Auth:** Required
- **Behavior:** Fetch user scoped to tenant. Find address by `addressId`. If not found → `404`. Remove from set, save. Evict `userInitCache` for `userId`.
- **Response:** `200 { message: "Address deleted successfully" }`

---

### 3.3 Tenant — `/api/v1/tenant`

#### `GET /current`
- **Auth:** Required
- **Behavior:** Extract `tenantId` from JWT via `UserContextUtil.getTenantId()`, delegate to `tenantService.getTenantById(tenantId)`.
- **Response:** `200 TenantDto`

#### `PUT /{tenantId}/toggle-status`
- **Auth:** Required
- **Behavior:** Fetch tenant. Flip `isActive`. If deactivating and tenant has active users, throw `CommonException("Cannot deactivate tenant with active users", 409 CONFLICT)`.
- **Response:** `200 { message: "Tenant status toggled. Current status: Active/Inactive" }`

#### `DELETE /{tenantId}/address/{addressId}`
- **Auth:** Required
- **Behavior:** Fetch tenant. Find address by `addressId` in `tenant.getAddresses()`. If not found → `404`. Remove from set, save.
- **Response:** `200 { message: "Tenant address deleted successfully" }`

---

## 4. Implementation Order

**Layer 1 — Bugs + Error Handling (implement first, verify before Layer 2):**
1. Fix `generateTenantCode` (safest, no dependencies)
2. Fix `AuthController.initUser` NPE
3. Add `isActive` check in `AuthService.signIn`
4. Fix OTP validation with Spring Cache (new cache key, store on register, validate on verify)
5. Replace all `RuntimeException` / `IllegalStateException` in services with `CommonException`
6. Fix HTTP method mismatches in controllers
7. Fix `searchUsers` unbounded query
8. Harden `GlobalExceptionHandler` with all new handlers

**Layer 2 — Missing APIs (after Layer 1 is verified):**
1. Auth: `signout`, `forgot-password`, `reset-password`, `resend-otp`
2. User: `GET /me`, `GET /{userId}/addresses`, `DELETE /{userId}/address/{addressId}`
3. Tenant: `GET /current`, `PUT /{tenantId}/toggle-status`, `DELETE /{tenantId}/address/{addressId}`

---

## 5. Constraints

- No new database tables or schema changes — OTP uses Spring Cache only.
- All new endpoints follow existing `ResponseResource<T>` response envelope.
- All new endpoints respect existing tenant-scoping via `UserContextUtil`.
- `@Valid` added to all new request DTOs.
- Password reset OTP and tenant verify OTP use separate cache key namespaces to avoid collisions.
