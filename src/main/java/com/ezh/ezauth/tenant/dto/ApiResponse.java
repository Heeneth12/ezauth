package com.ezh.ezauth.tenant.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse {
    private Boolean success;
    private String message;
    private Object data;
}