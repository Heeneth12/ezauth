package com.ezh.ezauth.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserMiniDto {
    private Long id;
    private String userType;
    private String UserUuid;
    private String name;

    public UserMiniDto(UserMiniDto userMini){
        this.id = userMini.getId();
        this.userType = userMini.getUserType();
        this.UserUuid = userMini.getUserUuid();
        this.name = userMini.getName();
    }
}
