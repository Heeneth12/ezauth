package com.ezh.ezauth.common.dto;

import com.ezh.ezauth.common.entity.Privilege;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleDto {
    private Long id;
    private String moduleName; // e.g., "Dashboard", "Billing"
    private String moduleKey; // e.g., "DASHBOARD", "BILLING"
    private String description;
    private Boolean isActive = true;
    private Set<PrivilegeDto> privileges;
}
