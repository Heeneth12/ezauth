package com.ezh.ezauth.integrations.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TestConnectionResponse {

    private Boolean connected;
    private String message;
    private LocalDateTime testedAt;
}
