package com.ezh.ezauth.user.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserApplicationDto {
    private Long id;
    private String appName;
    private String appKey;
    private Set<UserModulePrivilegeDto> modulePrivileges;
    private Boolean isActive = true;
    private LocalDateTime assignedAt;
}
