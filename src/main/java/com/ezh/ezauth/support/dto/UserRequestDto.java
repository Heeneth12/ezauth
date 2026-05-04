package com.ezh.ezauth.support.dto;

import com.ezh.ezauth.support.entity.SupportCategory;
import com.ezh.ezauth.support.entity.SupportPriority;
import com.ezh.ezauth.support.entity.SupportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDto {
    private String userReqUuid;
    private String userUuid;
    private String assignedUuid;
    private String contactEmail;
    private String contactName;
    private String subject;
    private String description;
    private String sourceUrl;
    private String sourceName;
    private SupportCategory category;
    private SupportStatus status;
    private SupportPriority priority;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}