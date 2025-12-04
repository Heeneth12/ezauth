package com.ezh.ezauth.common.dto;

import com.ezh.ezauth.common.entity.Module;
import lombok.*;

import java.util.Set;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationDto {
    private Long id;
    private String appName;
    private String appKey;
    private String description;
    private Boolean isActive = true;
    //private Set<ModuleDto> modules;
}
