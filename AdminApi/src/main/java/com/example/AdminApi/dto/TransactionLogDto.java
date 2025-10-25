package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for returning Transaction Log data to Admin Panel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLogDto {
    private Long id;
    private String correlationId;
    private String level;        // INFO, WARN, ERROR
    private String component;    // API, KAFKA_PRODUCER, KAFKA_CONSUMER, RULES, NOTIFICATION
    private String message;
    private String details;      // JSON string
    private String timestamp;    // ISO 8601 format
}
