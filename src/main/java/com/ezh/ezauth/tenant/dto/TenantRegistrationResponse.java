package com.ezh.ezauth.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantRegistrationResponse {
    private Long tenantId;
    private String tenantName;
    private String tenantCode;
    private Long adminUserId;
    private String adminEmail;
    private String message;
}
