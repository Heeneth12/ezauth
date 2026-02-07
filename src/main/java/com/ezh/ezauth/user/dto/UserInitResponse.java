package com.ezh.ezauth.user.dto;

import com.ezh.ezauth.tenant.entity.Tenant;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInitResponse {
    private Long id;
    private String userUuid;
    private String fullName;
    private String email;
    private String phone;
    private String userType;

    @Builder.Default
    private Boolean isActive = true;
    private long tenantId;
    private String tenantName;
    private Set<UserApplicationDto> userApplications;
    private Set<String> userRoles;
}
