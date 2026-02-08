package com.ezh.ezauth.auth.service;


import com.ezh.ezauth.auth.dto.AuthResponse;
import com.ezh.ezauth.auth.dto.GoogleSignInRequest;
import com.ezh.ezauth.auth.dto.SignInRequest;
import com.ezh.ezauth.auth.dto.TokenRefreshRequest;
import com.ezh.ezauth.security.JwtTokenProvider;
import com.ezh.ezauth.tenant.service.TenantService;
import com.ezh.ezauth.user.dto.UserInitResponse;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.entity.UserRole;
import com.ezh.ezauth.user.repository.UserRepository;
import com.ezh.ezauth.user.service.UserService;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final TenantService tenantService;

    @Value("${google.client.id}")
    private String googleClientId;


    @Transactional(readOnly = true)
    public AuthResponse signIn(SignInRequest request) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Fetch user
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Extract roles as comma-separated string
        String roles = extractUserRoles(user);

        // Generate tokens
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
                .message("Success")
                .build();
    }


    @Transactional(readOnly = true)
    public AuthResponse refreshToken(TokenRefreshRequest request) {

        String refreshToken = request.getRefreshToken();

        // Validate refresh token
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        // Get user ID from refresh token
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // Fetch user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Extract roles as comma-separated string
        String roles = extractUserRoles(user);

        // Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getTenant().getId(),
                user.getUserType().name(),
                roles
        );

        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse
                .builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .message("")
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

            // Extract roles as comma-separated string
            String roles = extractUserRoles(user);

            //Generate Tokens
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
