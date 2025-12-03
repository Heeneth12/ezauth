package com.ezh.ezauth.common.dto;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleDto {
    private Long id;
    private String roleName;
    private String roleKey;
    private String description;
    private Long tenantId;
}
