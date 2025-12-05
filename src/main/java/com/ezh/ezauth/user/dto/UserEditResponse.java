package com.ezh.ezauth.user.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEditResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private Boolean isActive;
    private List<Long> roleIds;

    // List of Applications assigned
    private List<UserAppEditDto> userApplications;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAppEditDto {
        private Long applicationId;
        private Boolean isActive;
        // The privileges assigned within this app
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
    }
}