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
    private  String userUuid;
    private String fullName;
    private String email;
    private String phone;
    private Set<String> roles;
    private List<UserRoleDto> userRoles;
    private Set<Long> applicationIds;
    private Boolean isActive;
    private Long tenantId;
}
