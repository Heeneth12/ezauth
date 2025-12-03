package com.ezh.ezauth.user.dto;

import com.ezh.ezauth.tenant.dto.TenantDto;
import com.ezh.ezauth.user.entity.UserApplication;
import com.ezh.ezauth.user.entity.UserRole;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private  String userUuid;
    private String fullName;
    private String email;
    private String phone;
    private Boolean isActive = true;
    private Long tenantId;
    private TenantDto tenant;
    private Set<UserApplication> userApplications;
    private Set<UserRole> userRoles;
}
