package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageAlertDto {
    private Long ruleID;
    private String ruleName;
    private String reason;
    private LocalDateTime sendingTime;
    private UUID transactionCorrelationId;
}
