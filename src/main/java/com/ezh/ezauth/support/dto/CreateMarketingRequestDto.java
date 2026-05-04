package com.ezh.ezauth.support.dto;

import com.ezh.ezauth.support.entity.SupportCategory;
import com.ezh.ezauth.support.entity.SupportPriority;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateMarketingRequestDto {

    @NotBlank(message = "Contact email is required")
    @Email(message = "Please provide a valid email address")
    private String contactEmail;

    private String contactName;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Description is required")
    private String description;

    private SupportCategory category;

    private SupportPriority priority;

    private String sourceUrl;

    private String sourceName;

    private Map<String, Object> metadata;
}
