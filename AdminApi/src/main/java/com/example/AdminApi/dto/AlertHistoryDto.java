package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertHistoryDto {
    private Long id;
    private Long ruleId;
    private String ruleName;
    private String reason;
    private LocalDateTime sendingTime;
    private UUID transactionCorrelationId;
}
