package com.ezh.ezauth.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@Getter
public class JwtAuthentication extends AbstractAuthenticationToken {

    private final Long userId;
    private final String userUuid;
    private final String email;
    private final Long tenantId;
    private final String tenantUuid;
    private final Long branchId;
    private final String userType;
    private final String roles;

    public JwtAuthentication(Long userId, String userUuid, String email, Long tenantId, String tenantUuid, Long branchId, String userType, String roles) {
        super(parseAuthorities(roles));
        this.userId = userId;
        this.userUuid = userUuid;
        this.email = email;
        this.tenantId = tenantId;
        this.tenantUuid = tenantUuid;
        this.branchId = branchId;
        this.userType = userType;
        this.roles = roles;
        setAuthenticated(true);
    }

    private static Collection<? extends GrantedAuthority> parseAuthorities(String roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(roles.split(","))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim()))
                .collect(Collectors.toList());
    }

    @Override
    public Object getCredentials() {
        return null; // JWT token already validated by the filter
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }
}