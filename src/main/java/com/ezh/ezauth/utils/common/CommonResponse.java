package com.ezh.ezauth.utils.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonResponse{
    private String id;
    private String message;
    private Status status;
}
