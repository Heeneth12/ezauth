package com.ezh.ezauth.subscription.dto;

import com.ezh.ezauth.subscription.entity.PlanType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlanSummaryDto {
    private Long id;
    private String name;
    private String description;
    private PlanType type;
    private BigDecimal price;
    private Integer durationDays;
    private Integer maxUsers;
    private Boolean isActive;
    private MiniApplicationDto application;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MiniApplicationDto {
        private Long id;
        private String appName;
        private String appKey;
    }
}
