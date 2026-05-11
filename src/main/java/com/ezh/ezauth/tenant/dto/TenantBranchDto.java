package com.ezh.ezauth.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantBranchDto {
    private Long id;
    private String branchUuid;

    @NotBlank(message = "Branch name is required")
    @Size(min = 2, max = 100, message = "Branch name must be between 2 and 100 characters")
    private String branchName;

    private String branchCode;
    private String description;

    @Email(message = "Invalid branch contact email format")
    private String contactEmail;
    private String contactPhone;
    private String addressLine1;
    private String addressLine2;
    private String route;
    private String area;
    private String city;
    private String state;
    private String country;
    private String pinCode;
    private Boolean isActive;
}
