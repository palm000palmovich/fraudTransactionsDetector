package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WebhookAlertDto {
    private Long ruleID;
    private String ruleName;
    private String reason;
    private LocalDateTime sendingTime;
    private UUID transactionCorrelationId;
    private String alertType = "FRAUD_ALERT";
    private String source = "FRAUD_DETECTION_SERVICE";
    private Instant timestamp = Instant.now();
    private String status = "NEW";
    private Map<String, Object> metadata = new HashMap<>();
}
