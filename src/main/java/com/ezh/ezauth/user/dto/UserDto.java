package com.ezh.ezauth.user.dto;

import lombok.*;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private Long tenantId;
    private String userUuid;
    private String fullName;
    private String email;
    private String phone;
    private Boolean isActive;
    private String userType;
    private Set<String> roles;
    private List<UserRoleDto> userRoles;
    private Set<Long> applicationIds;
    private Set<UserAddressDto> userAddress;
    private List<UserAppEditDto> userApplications;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAppEditDto {
        private Long applicationId;
        private String appName;
        private Boolean isActive;
        private List<UserModulePrivilegeDto> modulePrivileges;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserModulePrivilegeDto {
        private Long moduleId;
        private Long privilegeId;
        private String privilegeName; // e.g., "Read Only"
        private String privilegeKey;  // e.g., "READ"
    }
}