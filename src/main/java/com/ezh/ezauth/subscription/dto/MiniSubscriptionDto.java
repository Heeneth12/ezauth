package com.ezh.ezauth.subscription.dto;

import com.ezh.ezauth.subscription.entity.SubscriptionStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MiniSubscriptionDto {
    private Long id;
    private SubscriptionStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean isValid;
    private long daysRemaining;
}
