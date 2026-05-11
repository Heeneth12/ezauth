package com.ezh.ezauth.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;


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
}