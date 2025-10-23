package com.example.AdminApi.controllers;

import com.example.AdminApi.dto.IdempotencyCache;
import com.example.AdminApi.dto.MakeTransactionDto;
import com.example.AdminApi.services.IdempotencyService;
import com.example.AdminApi.services.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(path = "/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final IdempotencyService idempotencyService;
    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<?> makeTransaction(@RequestHeader(value = "Idempotency-key", required = true) String idempotencyKey,
                                             @RequestBody @Valid MakeTransactionDto makeTransactionDto) {
        // Validate that idempotency key is not blank
        if (idempotencyKey.isBlank()) {
            log.warn("Received blank Idempotency-key header");
            return ResponseEntity
                .badRequest()
                .body(Map.of(
                    "error", "BAD_REQUEST",
                    "message", "Idempotency-key header cannot be empty"
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

}
