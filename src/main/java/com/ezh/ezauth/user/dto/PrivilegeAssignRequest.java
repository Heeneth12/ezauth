package com.ezh.ezauth.user.dto;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrivilegeAssignRequest {
    private Long applicationId;
    private Long moduleId;
    private Set<Long> privilegeIds;
}
