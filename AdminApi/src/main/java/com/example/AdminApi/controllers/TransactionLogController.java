package com.example.AdminApi.controllers;

import com.example.AdminApi.dto.PagedResponse;
import com.example.AdminApi.dto.TransactionLogDto;
import com.example.AdminApi.services.TransactionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * REST Controller for transaction logs.
 *
 * Access control:
 * - GET /api/transaction-logs: Available to ADMIN and VIEWER roles
 *
 * Provides detailed logs tracking transaction lifecycle:
 * API -> Kafka Producer -> Kafka Consumer -> Rules -> Notification
 */
@Slf4j
@RestController
@RequestMapping("/api/transaction-logs")
@RequiredArgsConstructor
public class TransactionLogController {

    private final TransactionLogService transactionLogService;

    /**
     * GET /api/transaction-logs - Get transaction logs with filtering and pagination
     *
     * Query params:
     * - correlationId: UUID of transaction (optional)
     * - level: INFO, WARN, ERROR (optional)
     * - component: API, KAFKA_PRODUCER, KAFKA_CONSUMER, RULES, NOTIFICATION (optional)
     * - dateFrom: Start date (optional, default: 7 days ago)
     * - dateTo: End date (optional, default: now)
     * - page: Page number (default: 0)
     * - size: Page size (default: 50)
     * - sort: newest or oldest (default: newest)
     */
    @GetMapping
    public ResponseEntity<PagedResponse<TransactionLogDto>> getTransactionLogs(
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String component,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "newest") String sort) {

        log.info("GET /api/transaction-logs - correlationId={}, level={}, component={}, dateFrom={}, dateTo={}, page={}, size={}, sort={}",
                correlationId, level, component, dateFrom, dateTo, page, size, sort);

        PagedResponse<TransactionLogDto> logs = transactionLogService.getLogs(
                correlationId,
                level,
                component,
                dateFrom,
                dateTo,
                page,
                size,
                sort
        );

        return ResponseEntity.ok(logs);
    }
}
