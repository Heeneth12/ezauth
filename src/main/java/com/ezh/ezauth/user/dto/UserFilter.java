package com.ezh.ezauth.user.dto;

import com.ezh.ezauth.user.entity.UserType;
import lombok.*;

import java.util.List;

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
    private List<UserType> userType;
    private Boolean isActive;
}
