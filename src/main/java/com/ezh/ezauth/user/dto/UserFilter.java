package com.ezh.ezauth.user.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFilter {
    private Long tenantId;
    private Long userId;
    private String userUuid;
    private String email;
    private String phone;
    private String searchQuery;
}
