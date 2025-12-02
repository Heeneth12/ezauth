package com.ezh.ezauth.user.dto;

import lombok.*;

import java.util.Set;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserModulePrivilegeDto {
    private String moduleKey;
    private Set<String> privilegeKey;
}
