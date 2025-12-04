package com.ezh.ezauth.user.dto;

import lombok.*;


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
    private String role;
    private Boolean isActive;
    private Long tenantId;
}
