package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO для метрик правила fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleMetricsDto {
    private Long ruleId;
    private String ruleName;
    private String ruleType;
    private Boolean enabled;

    // Метрики срабатываний
    private Long totalTriggers;           // Всего срабатываний
    private Long triggersLast24h;         // Срабатываний за последние 24 часа
    private Long triggersLast7d;          // Срабатываний за последние 7 дней

    // Метрики производительности
    private Double avgLatencyMs;          // Среднее время выполнения (мс)
    private Double maxLatencyMs;          // Максимальное время выполнения (мс)
    private Double minLatencyMs;          // Минимальное время выполнения (мс)

    // Метрики ошибок
    private Long totalErrors;             // Всего ошибок
    private Long errorsLast24h;           // Ошибок за последние 24 часа

    // Временные метки
    private LocalDateTime lastTriggered;  // Последнее срабатывание
    private LocalDateTime calculatedAt;   // Когда рассчитаны метрики
}
