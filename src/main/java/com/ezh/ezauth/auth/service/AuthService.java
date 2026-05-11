package com.ezh.ezauth.auth.service;


import com.ezh.ezauth.auth.dto.AuthResponse;
import com.ezh.ezauth.auth.dto.ForgotPasswordRequest;
import com.ezh.ezauth.auth.dto.GoogleSignInRequest;
import com.ezh.ezauth.auth.dto.ResetPasswordRequest;
import com.ezh.ezauth.auth.dto.SignInRequest;
import com.ezh.ezauth.auth.dto.TokenRefreshRequest;
import com.ezh.ezauth.security.JwtTokenProvider;
import com.ezh.ezauth.subscription.service.SubscriptionService;
import com.ezh.ezauth.tenant.service.TenantService;
import com.ezh.ezauth.user.dto.UserInitResponse;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.entity.UserRole;
import com.ezh.ezauth.user.repository.UserRepository;
import com.ezh.ezauth.user.service.UserService;
import com.ezh.ezauth.utils.EmailService;
import com.ezh.ezauth.utils.UserContextUtil;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.util.Collections;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final TenantService tenantService;
    private final SubscriptionService subscriptionService;
    private final CacheManager cacheManager;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${google.client.id}")
    private String googleClientId;


    @Transactional(readOnly = true)
    public AuthResponse signIn(SignInRequest request)  throws CommonException{
        // Fetch user
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CommonException("Invalid Email", HttpStatus.UNAUTHORIZED));

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            // If email was right but password was wrong
            throw new CommonException("Invalid password", HttpStatus.UNAUTHORIZED);
        }

        if (!user.getIsActive()) {
            throw new CommonException("Account is inactive. Contact your administrator.", HttpStatus.FORBIDDEN);
        }

        if (!subscriptionService.hasValidSubscription(user.getTenant().getId())) {
            throw new CommonException("Your organization's subscription has expired or is inactive. Please contact support.", HttpStatus.FORBIDDEN);
        }

        // Extract roles as comma-separated string
        String roles = extractUserRoles(user);

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUserUuid(),
                user.getEmail(),
                user.getTenant().getId(),
                user.getTenant().getTenantUuid(),
                user.getBranch() != null ? user.getBranch().getId() : null,
                user.getUserType().name(),
                roles
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .message("Success")
                .build();
    }


    @Transactional(readOnly = true)
    public AuthResponse refreshToken(TokenRefreshRequest request) {

        String refreshToken = request.getRefreshToken();

        // Validate refresh token
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new CommonException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
        }

        // Get user ID from refresh token
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // Fetch user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        // Subscription Validation Check (in case it expired while they were logged in)
        if (!subscriptionService.hasValidSubscription(user.getTenant().getId())) {
            throw new CommonException("Your organization's subscription has expired or is inactive.", HttpStatus.FORBIDDEN);
        }

        // Extract roles as comma-separated string
        String roles = extractUserRoles(user);

        // Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUserUuid(),
                user.getEmail(),
                user.getTenant().getId(),
                user.getTenant().getTenantUuid(),
                user.getBranch() != null ? user.getBranch().getId() : null,
                user.getUserType().name(),
                roles
        );

        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse
                .builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .message("Token refreshed successfully")
                .build();
    }

    /**
     * CACHE IMPLEMENTATION:
     * value = "userInitCache" -> The specific cache container to use
     * key = SpEL expression -> Extracts userId from token for cache key
     * unless = "#result == null" -> Don't cache errors or nulls
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "userInitCache", key = "@jwtTokenProvider.getUserIdFromToken(#token)", unless = "#result == null")
    public UserInitResponse initUser(String token) throws CommonException {

        // This log only prints if the data was NOT found in cache (Cache Miss)
        log.info("Fetching User Init Data from Database (Cache Miss)");

        if (token == null || token.isBlank()) {
            throw new CommonException("Token is missing", HttpStatus.BAD_REQUEST);
        }

        if (!jwtTokenProvider.validateToken(token)) {
            throw new CommonException("Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }

        // Extract userId from token first for cache key
        Long userId;
        try {
            userId = jwtTokenProvider.getUserIdFromToken(token);
        } catch (Exception e) {
            throw new CommonException("Invalid user information in token", HttpStatus.UNAUTHORIZED);
        }

        return userService.getUserInitDetails(userId);
    }

    @Transactional(readOnly = true)
    public CommonResponse validateToken(String token) throws CommonException {
        log.info("Execution started: Validating access token integrity and expiration");

        boolean isValid = jwtTokenProvider.validateToken(token);

        //Explicitly handle invalid cases if no exception was thrown
        if (!isValid) {
            log.warn("Token validation failed: Invalid signature or expired");
            throw new CommonException("Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }

        return CommonResponse.builder()
                .status(Status.SUCCESS)
                .message("Token is valid")
                .build();
    }

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

    @Transactional
    public AuthResponse signInWithGoogle(GoogleSignInRequest request) throws CommonException {
        try {
            //Verify Google Token
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getIdToken());

            if (idToken == null) {
                throw new CommonException("Invalid Google ID Token", HttpStatus.UNAUTHORIZED);
            }

            //Extract Info
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            //Find OR Register User
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                user = tenantService.registerGoogleTenant(email, name, pictureUrl, request.getAppKey());
            }

            if (!subscriptionService.hasValidSubscription(user.getTenant().getId())) {
                throw new CommonException("Your organization's subscription has expired or is inactive.", HttpStatus.FORBIDDEN);
            }

            // Extract roles as comma-separated string
            String roles = extractUserRoles(user);

            //Generate Tokens
            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(),
                    user.getUserUuid(),
                    user.getEmail(),
                    user.getTenant().getId(),
                    user.getTenant().getTenantUuid(),
                    user.getBranch() != null ? user.getBranch().getId() : null,
                    user.getUserType().name(),
                    roles
            );
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .message("Google Sign-In Successful")
                    .build();

        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google Sign-In failed", e);
            throw new CommonException("Google Authentication Failed", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Extract active user roles as comma-separated string
     * @param user User entity with roles
     * @return Comma-separated role keys (e.g., "ADMIN,VIEWER") or empty string
     */
    private String extractUserRoles(User user) {
        if (user.getUserRoles() == null) {
            return "";
        }
        return user.getUserRoles().stream()
                .filter(UserRole::getIsActive)
                .map(ur -> ur.getRole().getRoleKey())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }
}
