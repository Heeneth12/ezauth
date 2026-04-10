package com.ezh.ezauth.subscription.dto;

import com.ezh.ezauth.subscription.entity.PlanType;
import com.ezh.ezauth.subscription.entity.SubscriptionPlan;
import lombok.*;


import java.math.BigDecimal;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlanDto {
    private Long id;
    private String name;
    private String description;
    private PlanType type;
    private BigDecimal price;
    private Integer durationDays;
    private Integer maxUsers;
    private Boolean isActive;
}
