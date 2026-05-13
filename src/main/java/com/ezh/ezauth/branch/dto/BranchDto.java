package com.ezh.ezauth.branch.dto;

import com.ezh.ezauth.common.dto.AddressDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

public class BranchDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "Branch name is required")
        @Size(max = 255)
        private String branchName;

        @NotBlank(message = "Branch code is required")
        @Size(max = 100)
        private String branchCode;

        private Boolean isHeadOffice = false;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        @Size(max = 255)
        private String branchName;

        private Boolean isHeadOffice;
        private Boolean isActive;
    }

    // RESPONSE

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String branchName;
        private String branchCode;
        private Boolean isHeadOffice;
        private Boolean isActive;
        private Long tenantId;
        private String tenantName;
        private int userCount;
        private List<AddressDto> addresses;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Summary {
        private Long id;
        private String branchName;
        private String branchCode;
        private Boolean isHeadOffice;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserItem {
        private Long id;
        private String userUuid;
        private String name;
        private String email;
        private String phone;
        private String userType;
    }
}