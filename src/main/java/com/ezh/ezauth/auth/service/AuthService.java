package com.ezh.ezauth.auth.service;


import com.ezh.ezauth.auth.dto.AuthResponse;
import com.ezh.ezauth.auth.dto.SignInRequest;
import com.ezh.ezauth.auth.dto.TokenRefreshRequest;
import com.ezh.ezauth.security.JwtTokenProvider;
import com.ezh.ezauth.user.dto.UserInitResponse;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.repository.UserRepository;
import com.ezh.ezauth.user.service.UserService;
import com.ezh.ezauth.utils.exception.CommonException;
import io.jsonwebtoken.Jwt;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;


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

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getTenant().getId()
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

        // Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getTenant().getId()
        );

        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse
                .builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .message("")
                .build();
    }

    @Transactional(readOnly = true)
    public UserInitResponse initUser(String token) throws CommonException {

        if (token == null || token.isBlank()) {
            throw new CommonException("Token is missing", HttpStatus.BAD_REQUEST);
        }

        if (!jwtTokenProvider.validateToken(token)) {
            throw new CommonException("Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }

        Long userId;
        try {
            userId = jwtTokenProvider.getUserIdFromToken(token);
        } catch (Exception e) {
            // 401 – Unauthorized
            throw new CommonException("Invalid user information in token", HttpStatus.UNAUTHORIZED);
        }

        return userService.getUserInitDetails(userId);
    }

}
