package com.example.AdminApi.controllers;

import com.example.AdminApi.dto.PagedResponse;
import com.example.AdminApi.dto.StatsDto;
import com.example.AdminApi.dto.TransactionResponseDto;
import com.example.AdminApi.services.AdminTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST Controller for retrieving transactions (Admin Panel).
 */
@RestController
@RequestMapping("/api/admin/transactions")
@RequiredArgsConstructor
@Slf4j
public class AdminTransactionController {

    private final AdminTransactionService adminTransactionService;

    /**
     * GET /api/admin/transactions - Get all transactions with optional filtering and pagination
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
        log.info("GET /api/admin/transactions - Fetching transactions: status={}, correlationId={}, sourceId={}, destinationId={}, dateFrom={}, dateTo={}, page={}, size={}, sort={}",
                 status, correlationId, sourceId, destinationId, dateFrom, dateTo, page, size, sort);
        PagedResponse<TransactionResponseDto> transactions =
            adminTransactionService.getAllTransactions(status, correlationId, sourceId, destinationId, dateFrom, dateTo, page, size, sort);
        return ResponseEntity.ok(transactions);
    }

    /**
     * GET /api/admin/transactions/{id} - Get transaction by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponseDto> getTransactionById(@PathVariable Long id) {
        log.info("GET /api/admin/transactions/{} - Fetching transaction", id);
        TransactionResponseDto transaction = adminTransactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction);
    }

    /**
     * GET /api/admin/transactions/stats - Get transaction statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsDto> getStats() {
        log.info("GET /api/admin/transactions/stats - Fetching statistics");
        StatsDto stats = adminTransactionService.getStats();
        return ResponseEntity.ok(stats);
    }
}
