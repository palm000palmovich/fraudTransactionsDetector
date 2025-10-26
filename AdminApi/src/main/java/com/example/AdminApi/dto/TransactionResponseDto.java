package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

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

    /**
     * Metadata from rule evaluation (e.g., ML scores, thresholds, model versions).
     *
     * For ML rules, contains:
     * - ml_score (Float): fraud score from ML model (0.0 - 1.0)
     * - ml_threshold (Double): threshold used for decision
     * - ml_model_version (String): model version identifier
     *
     * Example:
     * {
     *   "ml_score": 0.9234,
     *   "ml_threshold": 0.5,
     *   "ml_model_version": "production-15f"
     * }
     */
    private Map<String, Object> ruleMetadata;
}
