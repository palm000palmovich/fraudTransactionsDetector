package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for returning Transaction data to Admin Panel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponseDto {
    private Long id;
    private String correlationId;
    private String sourceId;
    private String destinationId;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime timestamp;
    private String status;
    private Long triggeredRuleId;
    private String triggeredRuleName;
    private String triggerReason;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
}
