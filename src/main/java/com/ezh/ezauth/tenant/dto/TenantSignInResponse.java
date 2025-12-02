package com.ezh.ezauth.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSignInResponse {
    private String token;
    private Long userId;
    private String userUuid;
    private String fullName;
    private String email;
    private Long tenantId;
    private String tenantUuid;
    private String tenantName;
    private String tenantCode;
    private Boolean isAdmin;
    private String message;
}
