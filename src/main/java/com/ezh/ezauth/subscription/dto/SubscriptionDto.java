package com.ezh.ezauth.subscription.dto;


import com.ezh.ezauth.subscription.entity.Subscription;
import com.ezh.ezauth.subscription.entity.SubscriptionStatus;

import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionDto {
    private Long id;
    private SubscriptionPlanDto plan;
    private SubscriptionStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean autoRenew = false;
    private LocalDateTime createdAt;
    private boolean isValid;
    private long daysRemaining;

    public static SubscriptionDto fromEntity(Subscription entity) {
        if (entity == null) return null;

        // Calculate days remaining safely
        long remaining = 0;
        if (entity.getEndDate() != null && LocalDateTime.now().isBefore(entity.getEndDate())) {
            remaining = Duration.between(LocalDateTime.now(), entity.getEndDate()).toDays();
        }

        return SubscriptionDto.builder()
                .id(entity.getId())
                .status(entity.getStatus())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .autoRenew(entity.getAutoRenew())
                .createdAt(entity.getCreatedAt())
                .plan(SubscriptionPlanDto.fromEntity(entity.getPlan()))
                .isValid(entity.isValid())
                .daysRemaining(remaining)
                .build();
    }

}
