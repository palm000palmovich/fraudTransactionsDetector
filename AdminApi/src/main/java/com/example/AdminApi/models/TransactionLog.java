package com.example.AdminApi.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "level", nullable = false, length = 10)
    private String level; // INFO, WARN, ERROR

    @Column(name = "component", nullable = false, length = 50)
    private String component; // API, KAFKA_PRODUCER, KAFKA_CONSUMER, RULES, NOTIFICATION

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details; // JSON format

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
