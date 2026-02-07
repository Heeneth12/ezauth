package com.ezh.ezauth.user.dto;

import com.ezh.ezauth.user.entity.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotBlank(message = "Password number is required")
    private String password;

    @NotNull(message = "User type is required")
    private UserType userType;

    private Set<Long> roleIds;
    private Set<Long> applicationIds;
    private List<PrivilegeAssignRequest> privilegeMapping;
    private Set<UserAddressDto> address;
}
