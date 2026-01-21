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

    public static SubscriptionPlanDto fromEntity(SubscriptionPlan entity) {
        if (entity == null) return null;

        return SubscriptionPlanDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .type(entity.getType())
                .price(entity.getPrice())
                .durationDays(entity.getDurationDays())
                .maxUsers(entity.getMaxUsers())
                .isActive(entity.getIsActive())
                .build();
    }

    public SubscriptionPlan toEntity() {
        return SubscriptionPlan.builder()
                .id(this.id)
                .name(this.name)
                .description(this.description)
                .type(this.type)
                .price(this.price)
                .durationDays(this.durationDays)
                .maxUsers(this.maxUsers)
                .isActive(this.isActive != null ? this.isActive : true)
                .build();
    }
}
