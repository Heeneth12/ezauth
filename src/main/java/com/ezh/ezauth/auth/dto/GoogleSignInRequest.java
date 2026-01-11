package com.ezh.ezauth.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleSignInRequest {
    @NotBlank(message = "ID Token is required")
    private String idToken;
    private String appKey;
}