package com.example.AdminApi.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntity {

    // Primary Key
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Correlation ID for tracing
    @Column(name = "correlation_id", nullable = false, unique = true)
    private UUID correlationId;

    // Transaction data
    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(name = "destination_id", nullable = false, length = 100)
    private String destinationId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "channel", length = 50)
    private String channel;

    @Column(name = "geo", length = 100)
    private String geo;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Processing status
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";  // PENDING, PROCESSED, ALERTED, REVIEWED

    // *** FIRST-MATCH: only the FIRST triggered rule ***
    @Column(name = "triggered_rule_id")
    private Long triggeredRuleId;

    @Column(name = "triggered_rule_name")
    private String triggeredRuleName;

    @Column(name = "trigger_reason", columnDefinition = "TEXT")
    private String triggerReason;

    // Metadata
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // Automatically set timestamps
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
