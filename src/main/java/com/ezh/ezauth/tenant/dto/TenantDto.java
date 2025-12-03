package com.ezh.ezauth.tenant.dto;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantDto {
    private Long id;
    private String tenantUuid;
    private String tenantName;
    private String tenantCode;
    private Boolean isActive;
}
