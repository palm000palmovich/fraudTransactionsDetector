package com.example.AdminApi.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "rule_id")
    private Long ruleID;
    @Column(name = "rule_name")
    private String ruleName;
    @Column(name = "reason")
    private String reason;
    @Column(name = "sending_time")
    private LocalDateTime sendingTime;
    @Column(name = "correlation_id")
    private UUID transactionCorrelationId;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
