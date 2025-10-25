package com.example.AdminApi.controllers;

import com.example.AdminApi.dto.IdempotencyCache;
import com.example.AdminApi.dto.MakeTransactionDto;
import com.example.AdminApi.dto.PagedResponse;
import com.example.AdminApi.dto.StatsDto;
import com.example.AdminApi.dto.TransactionResponseDto;
import com.example.AdminApi.services.AdminTransactionService;
import com.example.AdminApi.services.IdempotencyService;
import com.example.AdminApi.services.TransactionLogService;
import com.example.AdminApi.services.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller for transactions.
 *
 * Access control:
 * - POST /api/transactions: Public (no authentication required)
 * - GET /api/transactions: Available to ADMIN and VIEWER roles
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final IdempotencyService idempotencyService;
    private final TransactionService transactionService;
    private final AdminTransactionService adminTransactionService;
    private final TransactionLogService transactionLogService;

    @PostMapping
    public ResponseEntity<?> makeTransaction(@RequestHeader(value = "Idempotency-key", required = false) String idempotencyKey,
                                             @RequestBody @Valid MakeTransactionDto makeTransactionDto) {
        // Validate that idempotency key is present and not blank
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("Received blank or null Idempotency-key header: {}", idempotencyKey);
            return ResponseEntity
                .badRequest()
                .body(Map.of(
                    "status", "BAD_REQUEST",
                    "message", "Idempotency-key header is required and cannot be empty",
                    "timestamp", LocalDateTime.now().toString()
                ));
        }

        log.info("Processing transaction with Idempotency-key: {}", idempotencyKey);

        // Check if response is already cached
        Optional<IdempotencyCache> idempotencyCache = idempotencyService.getCachedResponse(idempotencyKey);
        if (idempotencyCache.isPresent()) {
            log.info("Returning cached response for idempotency key: {}", idempotencyKey);
            return idempotencyService.convertToResponse(idempotencyCache.get().getCachedResponse());
        }

        // Process new transaction
        UUID correlationId = transactionService.sendTransactionToTopic(makeTransactionDto);

        // Log transaction received
        transactionLogService.log(
                correlationId,
                "INFO",
                "API",
                "Transaction received via API",
                String.format("{\"from\":\"%s\",\"to\":\"%s\",\"amount\":%s,\"idempotencyKey\":\"%s\"}",
                        makeTransactionDto.getFrom(),
                        makeTransactionDto.getTo(),
                        makeTransactionDto.getAmount(),
                        idempotencyKey)
        );

        Map<String, Object> response = Map.of(
                "correlationId", correlationId.toString(),
                "idempotency-key", idempotencyKey,
                "status", "ACCEPTED",
                "message", "Transaction is being processed"
        );

        ResponseEntity<?> responseEntity = ResponseEntity.status(202).body(response);

        // Cache the response
        idempotencyService.cacheResponse(idempotencyKey, makeTransactionDto, responseEntity);

        return responseEntity;
    }

    /**
     * GET /api/transactions - Get all transactions with optional filtering and pagination
     * Query params: ?status=ALERTED&correlationId=xxx&sourceId=xxx&destinationId=xxx&dateFrom=2025-01-01T00:00:00&dateTo=2025-12-31T23:59:59&page=0&size=10&sort=newest
     */
    @GetMapping
    public ResponseEntity<PagedResponse<TransactionResponseDto>> getAllTransactions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String sourceId,
            @RequestParam(required = false) String destinationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "newest") String sort) {
        log.info("GET /api/transactions - Fetching transactions: status={}, correlationId={}, sourceId={}, destinationId={}, dateFrom={}, dateTo={}, page={}, size={}, sort={}",
                 status, correlationId, sourceId, destinationId, dateFrom, dateTo, page, size, sort);
        PagedResponse<TransactionResponseDto> transactions =
            adminTransactionService.getAllTransactions(status, correlationId, sourceId, destinationId, dateFrom, dateTo, page, size, sort);
        return ResponseEntity.ok(transactions);
    }

    /**
     * GET /api/transactions/{id} - Get transaction by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponseDto> getTransactionById(@PathVariable Long id) {
        log.info("GET /api/transactions/{} - Fetching transaction", id);
        TransactionResponseDto transaction = adminTransactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction);
    }

    /**
     * GET /api/transactions/stats - Get transaction statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsDto> getStats() {
        log.info("GET /api/transactions/stats - Fetching statistics");
        StatsDto stats = adminTransactionService.getStats();
        return ResponseEntity.ok(stats);
    }
}
