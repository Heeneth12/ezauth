package com.ezh.ezauth.user.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;
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
    private Map<String, Set<String>> modulePrivileges;
    private Boolean isActive = true;
}
