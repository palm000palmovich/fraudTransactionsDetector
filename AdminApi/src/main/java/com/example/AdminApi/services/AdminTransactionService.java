package com.example.AdminApi.services;

import com.example.AdminApi.dto.PagedResponse;
import com.example.AdminApi.dto.StatsDto;
import com.example.AdminApi.dto.TransactionResponseDto;
import com.example.AdminApi.exceptions.ResourceNotFoundException;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for retrieving transactions for Admin Panel.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminTransactionService {

    private final TransactionRepository transactionRepository;

    /**
     * Get all transactions with optional filtering and pagination.
     */
    public PagedResponse<TransactionResponseDto> getAllTransactions(
            String status,
            String correlationId,
            String sourceId,
            String destinationId,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            int page,
            int size,
            String sortOrder) {
        log.info("Fetching transactions: status={}, correlationId={}, sourceId={}, destinationId={}, dateFrom={}, dateTo={}, page={}, size={}, sortOrder={}",
                 status, correlationId, sourceId, destinationId, dateFrom, dateTo, page, size, sortOrder);

        // Normalize status (convert "all" to null for query)
        if ("all".equalsIgnoreCase(status)) {
            status = null;
        }

        // Create sort direction (default: newest first = descending)
        Sort.Direction direction = "oldest".equalsIgnoreCase(sortOrder)
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        // Create pageable with sorting by createdAt
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt"));

        // Handle date nulls: use min/max dates if not specified
        LocalDateTime effectiveDateFrom = (dateFrom != null) ? dateFrom : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime effectiveDateTo = (dateTo != null) ? dateTo : LocalDateTime.of(2099, 12, 31, 23, 59);

        // Use unified filter method
        Page<TransactionEntity> transactionPage = transactionRepository.findByFilters(
            status,
            correlationId,
            sourceId,
            destinationId,
            effectiveDateFrom,
            effectiveDateTo,
            pageable
        );

        // Convert to DTO
        List<TransactionResponseDto> content = transactionPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return PagedResponse.<TransactionResponseDto>builder()
                .content(content)
                .currentPage(transactionPage.getNumber())
                .pageSize(transactionPage.getSize())
                .totalElements(transactionPage.getTotalElements())
                .totalPages(transactionPage.getTotalPages())
                .first(transactionPage.isFirst())
                .last(transactionPage.isLast())
                .build();
    }

    /**
     * Get transaction by ID.
     */
    public TransactionResponseDto getTransactionById(Long id) {
        log.info("Fetching transaction by id: {}", id);
        TransactionEntity transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        return convertToDto(transaction);
    }

    /**
     * Get transaction statistics.
     */
    public StatsDto getStats() {
        log.info("Fetching transaction statistics");

        long total = transactionRepository.count();
        long processed = transactionRepository.countByStatus("PROCESSED");
        long alerted = transactionRepository.countByStatus("ALERTED");
        long pending = transactionRepository.countByStatus("PENDING");

        return StatsDto.builder()
                .totalTransactions(total)
                .processedCount(processed)
                .alertedCount(alerted)
                .pendingCount(pending)
                .build();
    }

    /**
     * Convert TransactionEntity to TransactionResponseDto.
     */
    private TransactionResponseDto convertToDto(TransactionEntity entity) {
        return TransactionResponseDto.builder()
                .id(entity.getId())
                .correlationId(entity.getCorrelationId().toString())
                .sourceId(entity.getSourceId())
                .destinationId(entity.getDestinationId())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .timestamp(entity.getTimestamp())
                .status(entity.getStatus())
                .triggeredRuleId(entity.getTriggeredRuleId())
                .triggeredRuleName(entity.getTriggeredRuleName())
                .triggerReason(entity.getTriggerReason())
                .ruleMetadata(entity.getRuleMetadata())
                .processedAt(entity.getProcessedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
