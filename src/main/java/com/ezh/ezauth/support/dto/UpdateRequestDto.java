package com.ezh.ezauth.support.dto;

import com.ezh.ezauth.support.entity.SupportCategory;
import com.ezh.ezauth.support.entity.SupportPriority;
import com.ezh.ezauth.support.entity.SupportStatus;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateRequestDto {
    private SupportStatus status;
    private SupportPriority priority;
    private SupportCategory category;
    private String assignedUuid;
}
