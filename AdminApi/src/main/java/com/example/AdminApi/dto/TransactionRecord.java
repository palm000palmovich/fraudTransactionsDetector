package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Облегченная запись транзакции для хранения в истории (TransactionHistoryStore).
 *
 * Содержит только необходимые поля для Pattern правил, чтобы минимизировать
 * использование памяти.
 *
 * Используется для:
 * - Sliding window вычислений
 * - Подсчета агрегатов (count, sum, max/min)
 * - Pattern detection (rapid_small_transfers и т.д.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRecord {

    /**
     * Временная метка транзакции.
     * Используется для sliding window (фильтрация по времени).
     */
    private LocalDateTime timestamp;

    /**
     * Сумма транзакции.
     * Используется для агрегатов и проверки порогов.
     */
    private BigDecimal amount;

    /**
     * Отправитель (sourceId).
     * Используется для группировки "от кого".
     */
    private String sourceId;

    /**
     * Получатель (destinationId).
     * Используется для группировки "кому".
     */
    private String destinationId;

    /**
     * Валюта транзакции.
     * Используется для валютных правил и конвертации.
     */
    private String currency;

    /**
     * Correlation ID для трейсинга (опционально).
     * Полезно для отладки и логирования.
     */
    private String correlationId;
}
