package com.ezh.ezauth.user.dto;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRoleDto {
    private Long id;
    private String roleKey;
    private Long roleId;
}
