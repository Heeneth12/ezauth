package com.ezh.ezauth.user.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrivilegeDto {
    private String privilegeName; // e.g., "Read Only", "Edit"
    private String privilegeKey; // e.g., "READ", "WRITE", "DELETE"

}
