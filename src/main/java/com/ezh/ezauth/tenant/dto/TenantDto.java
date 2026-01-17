package com.ezh.ezauth.tenant.dto;


import com.ezh.ezauth.common.dto.ApplicationDto;
import com.ezh.ezauth.user.dto.UserDto;
import lombok.*;

import java.util.Set;

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
    private String email;
    private String phone;
    private Boolean isActive;
    private UserDto tenantAdmin;
    private Set<ApplicationDto> applications;
    private Set<TenantAddressDto> tenantAddress;
}
