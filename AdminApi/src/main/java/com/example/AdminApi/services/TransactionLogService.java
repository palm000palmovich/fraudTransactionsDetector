package com.example.AdminApi.services;

import com.example.AdminApi.dto.PagedResponse;
import com.example.AdminApi.dto.TransactionLogDto;
import com.example.AdminApi.models.TransactionLog;
import com.example.AdminApi.repositories.TransactionLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionLogService {

    private final TransactionLogRepository transactionLogRepository;

    /**
     * Log a transaction event
     */
    @Transactional
    public void log(UUID correlationId, String level, String component, String message, String details) {
        try {
            TransactionLog logEntry = TransactionLog.builder()
                    .correlationId(correlationId)
                    .level(level)
                    .component(component)
                    .message(message)
                    .details(details)
                    .timestamp(LocalDateTime.now())
                    .build();

            transactionLogRepository.save(logEntry);
        } catch (Exception e) {
            // Don't throw exception - logging should not break the main flow
            log.error("Failed to save transaction log: correlationId={}, component={}, message={}, error={}",
                    correlationId, component, message, e.getMessage());
        }
    }

    /**
     * Convenience method without details
     */
    @Transactional
    public void log(UUID correlationId, String level, String component, String message) {
        log(correlationId, level, component, message, null);
    }

    /**
     * Get logs with filters and pagination
     */
    @Transactional(readOnly = true)
    public PagedResponse<TransactionLogDto> getLogs(
            String correlationId,
            String level,
            String component,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            int page,
            int size,
            String sort) {

        // Parse correlationId
        UUID correlationUUID = null;
        if (correlationId != null && !correlationId.isBlank()) {
            try {
                correlationUUID = UUID.fromString(correlationId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format for correlationId: {}", correlationId);
                // Return empty page if UUID is invalid
                return PagedResponse.<TransactionLogDto>builder()
                        .content(java.util.Collections.emptyList())
                        .currentPage(page)
                        .pageSize(size)
                        .totalElements(0)
                        .totalPages(0)
                        .first(true)
                        .last(true)
                        .build();
            }
        }

        // Normalize empty strings to null
        String normalizedLevel = (level != null && level.isBlank()) ? null : level;
        String normalizedComponent = (component != null && component.isBlank()) ? null : component;

        // Set default date range if not provided (last 7 days)
        if (dateFrom == null) {
            dateFrom = LocalDateTime.now().minusDays(7);
        }
        if (dateTo == null) {
            dateTo = LocalDateTime.now();
        }

        // Make final copies for use in lambda
        final UUID finalCorrelationUUID = correlationUUID;
        final String finalLevel = normalizedLevel;
        final String finalComponent = normalizedComponent;
        final LocalDateTime finalDateFrom = dateFrom;
        final LocalDateTime finalDateTo = dateTo;

        // Create pageable with sorting
        Sort sorting = sort.equals("oldest")
                ? Sort.by(Sort.Direction.ASC, "timestamp")
                : Sort.by(Sort.Direction.DESC, "timestamp");
        Pageable pageable = PageRequest.of(page, size, sorting);

        // Build specification dynamically
        Specification<TransactionLog> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (finalCorrelationUUID != null) {
                predicates.add(criteriaBuilder.equal(root.get("correlationId"), finalCorrelationUUID));
            }
            if (finalLevel != null) {
                predicates.add(criteriaBuilder.equal(root.get("level"), finalLevel));
            }
            if (finalComponent != null) {
                predicates.add(criteriaBuilder.equal(root.get("component"), finalComponent));
            }
            if (finalDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("timestamp"), finalDateFrom));
            }
            if (finalDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("timestamp"), finalDateTo));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        // Fetch from repository
        Page<TransactionLog> logsPage = transactionLogRepository.findAll(spec, pageable);

        // Convert to PagedResponse
        return PagedResponse.<TransactionLogDto>builder()
                .content(logsPage.getContent().stream().map(this::toDto).toList())
                .currentPage(logsPage.getNumber())
                .pageSize(logsPage.getSize())
                .totalElements(logsPage.getTotalElements())
                .totalPages(logsPage.getTotalPages())
                .first(logsPage.isFirst())
                .last(logsPage.isLast())
                .build();
    }

    /**
     * Convert entity to DTO
     */
    private TransactionLogDto toDto(TransactionLog log) {
        return TransactionLogDto.builder()
                .id(log.getId())
                .correlationId(log.getCorrelationId().toString())
                .level(log.getLevel())
                .component(log.getComponent())
                .message(log.getMessage())
                .details(log.getDetails())
                .timestamp(log.getTimestamp().toString())
                .build();
    }
}
