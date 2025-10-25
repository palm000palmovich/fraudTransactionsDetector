package com.example.AdminApi.services;

import com.example.AdminApi.dto.AuditLogDto;
import com.example.AdminApi.dto.PagedResponse;
import com.example.AdminApi.models.AuditLog;
import com.example.AdminApi.repositories.AuditLogRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an audit event
     */
    @Transactional
    public void log(String username, String action, Long ruleId, String ruleName, String details) {
        try {
            AuditLog logEntry = AuditLog.builder()
                    .username(username)
                    .action(action)
                    .ruleId(ruleId)
                    .ruleName(ruleName)
                    .details(details)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditLogRepository.save(logEntry);
            log.info("Audit log created: user={}, action={}, ruleId={}", username, action, ruleId);
        } catch (Exception e) {
            // Don't throw exception - logging should not break the main flow
            log.error("Failed to save audit log: username={}, action={}, ruleId={}, error={}",
                    username, action, ruleId, e.getMessage());
        }
    }

    /**
     * Convenience method without details
     */
    @Transactional
    public void log(String username, String action, Long ruleId, String ruleName) {
        log(username, action, ruleId, ruleName, null);
    }

    /**
     * Get audit logs with filters and pagination
     */
    @Transactional(readOnly = true)
    public PagedResponse<AuditLogDto> getLogs(
            String username,
            String action,
            Long ruleId,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            int page,
            int size,
            String sort) {

        // Normalize empty strings to null
        String normalizedUsername = (username != null && username.isBlank()) ? null : username;
        String normalizedAction = (action != null && action.isBlank()) ? null : action;

        // Set default date range if not provided (last 30 days)
        if (dateFrom == null) {
            dateFrom = LocalDateTime.now().minusDays(30);
        }
        if (dateTo == null) {
            dateTo = LocalDateTime.now();
        }

        // Make final copies for use in lambda
        final String finalUsername = normalizedUsername;
        final String finalAction = normalizedAction;
        final Long finalRuleId = ruleId;
        final LocalDateTime finalDateFrom = dateFrom;
        final LocalDateTime finalDateTo = dateTo;

        // Create pageable with sorting
        Sort sorting = sort.equals("oldest")
                ? Sort.by(Sort.Direction.ASC, "timestamp")
                : Sort.by(Sort.Direction.DESC, "timestamp");
        Pageable pageable = PageRequest.of(page, size, sorting);

        // Build specification dynamically
        Specification<AuditLog> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (finalUsername != null) {
                predicates.add(criteriaBuilder.equal(root.get("username"), finalUsername));
            }
            if (finalAction != null) {
                predicates.add(criteriaBuilder.equal(root.get("action"), finalAction));
            }
            if (finalRuleId != null) {
                predicates.add(criteriaBuilder.equal(root.get("ruleId"), finalRuleId));
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
        Page<AuditLog> logsPage = auditLogRepository.findAll(spec, pageable);

        // Convert to PagedResponse
        return PagedResponse.<AuditLogDto>builder()
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
    private AuditLogDto toDto(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp().toString())
                .username(log.getUsername())
                .action(log.getAction())
                .ruleId(log.getRuleId())
                .ruleName(log.getRuleName())
                .details(log.getDetails())
                .build();
    }
}
