package com.example.AdminApi.services;

import com.example.AdminApi.dto.CreateRuleDto;
import com.example.AdminApi.dto.RuleDto;
import com.example.AdminApi.dto.RuleMetricsDto;
import com.example.AdminApi.dto.UpdateRuleDto;
import com.example.AdminApi.exceptions.ResourceNotFoundException;
import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.repositories.RuleRepository;
import com.example.AdminApi.repositories.TransactionRepository;
import com.example.AdminApi.validation.RuleParamsValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing fraud detection rules via Admin Panel.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RuleService {

    private final RuleRepository ruleRepository;
    private final TransactionRepository transactionRepository;
    private final MetricsService metricsService;
    private final RuleParamsValidator paramsValidator;

    /**
     * Get all rules.
     */
    public List<RuleDto> getAllRules() {
        log.info("Fetching all rules");
        return ruleRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get rule by ID.
     */
    public RuleDto getRuleById(Long id) {
        log.info("Fetching rule by id: {}", id);
        RuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found with id: " + id));
        return convertToDto(rule);
    }

    /**
     * Create new rule.
     */
    @Transactional
    public RuleDto createRule(CreateRuleDto dto, String creatorName) {
        log.info("Creating new rule: name={}, type={}", dto.getName(), dto.getRuleType());

        // VALIDATION: Validate JSON parameters before saving
        List<String> validationErrors = paramsValidator.validate(dto.getRuleType(), dto.getParamsJson());
        if (!validationErrors.isEmpty()) {
            String errorMsg = "Rule parameter validation failed: " + String.join(", ", validationErrors);
            log.error("Rule creation validation failed: {}", errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        RuleEntity rule = new RuleEntity();
        rule.setName(dto.getName());
        rule.setRuleType(dto.getRuleType());
        rule.setParamsJson(dto.getParamsJson());
        rule.setEnabled(dto.getEnabled());
        rule.setPriority(dto.getPriority());
        rule.setCreatedBy(creatorName);
        rule.setUpdatedBy(creatorName);
        // version defaults to 1

        RuleEntity saved = ruleRepository.save(rule);
        log.info("Rule created successfully: id={}", saved.getId());

        // Track rule creation
        metricsService.incrementRulesCreated();

        return convertToDto(saved);
    }

    /**
     * Update existing rule.
     */
    @Transactional
    public RuleDto updateRule(Long id, UpdateRuleDto dto, String updaterName) {
        log.info("Updating rule id={}", id);

        RuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found with id: " + id));

        // VALIDATION: If ruleType or paramsJson are being updated, validate
        // Determine final ruleType and paramsJson for validation
        var finalRuleType = dto.getRuleType() != null ? dto.getRuleType() : rule.getRuleType();
        var finalParamsJson = dto.getParamsJson() != null ? dto.getParamsJson() : rule.getParamsJson();

        // Validate if either ruleType or paramsJson is being changed
        if (dto.getRuleType() != null || dto.getParamsJson() != null) {
            List<String> validationErrors = paramsValidator.validate(finalRuleType, finalParamsJson);
            if (!validationErrors.isEmpty()) {
                String errorMsg = "Rule parameter validation failed: " + String.join(", ", validationErrors);
                log.error("Rule update validation failed for id={}: {}", id, errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
        }

        // Update fields if provided
        if (dto.getName() != null) {
            rule.setName(dto.getName());
        }
        if (dto.getRuleType() != null) {
            rule.setRuleType(dto.getRuleType());
        }
        if (dto.getParamsJson() != null) {
            rule.setParamsJson(dto.getParamsJson());
        }
        if (dto.getEnabled() != null) {
            rule.setEnabled(dto.getEnabled());
        }
        if (dto.getPriority() != null) {
            rule.setPriority(dto.getPriority());
        }

        rule.setUpdatedBy(updaterName);

        // Increment version
        rule.setVersion(rule.getVersion() + 1);

        RuleEntity updated = ruleRepository.save(rule);
        log.info("Rule updated successfully: id={}, version={}", updated.getId(), updated.getVersion());

        // Track rule update
        metricsService.incrementRulesUpdate();

        return convertToDto(updated);
    }

    /**
     * Delete rule by ID.
     */
    @Transactional
    public void deleteRule(Long id) {
        log.info("Deleting rule id={}", id);

        if (!ruleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Rule not found with id: " + id);
        }

        ruleRepository.deleteById(id);
        log.info("Rule deleted successfully: id={}", id);

        // Track rule deletion
        metricsService.incrementRulesDeleted();
    }

    /**
     * Toggle rule enabled/disabled.
     */
    @Transactional
    public RuleDto toggleRule(Long id) {
        log.info("Toggling rule id={}", id);

        RuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found with id: " + id));

        rule.setEnabled(!rule.isEnabled());
        rule.setVersion(rule.getVersion() + 1);

        RuleEntity updated = ruleRepository.save(rule);
        log.info("Rule toggled successfully: id={}, enabled={}", updated.getId(), updated.isEnabled());

        // Track rule update (toggle is also an update)
        metricsService.incrementRulesUpdate();

        return convertToDto(updated);
    }

    /**
     * Get metrics for a specific rule.
     */
    public RuleMetricsDto getRuleMetrics(Long ruleId) {
        log.info("Fetching metrics for rule id={}", ruleId);

        // Verify rule exists
        RuleEntity rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found with id: " + ruleId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusHours(24);
        LocalDateTime last7d = now.minusDays(7);

        // Count total triggers
        long totalTriggers = transactionRepository.countByTriggeredRuleId(ruleId);

        // Count triggers in time periods
        long triggersLast24h = transactionRepository.countByTriggeredRuleIdAndProcessedAtAfter(ruleId, last24h);
        long triggersLast7d = transactionRepository.countByTriggeredRuleIdAndProcessedAtAfter(ruleId, last7d);

        // Find last triggered transaction
        List<TransactionEntity> latestTransactions = transactionRepository
                .findLatestByTriggeredRuleId(ruleId, PageRequest.of(0, 1));
        LocalDateTime lastTriggered = latestTransactions.isEmpty() ? null : latestTransactions.get(0).getProcessedAt();

        // Build metrics DTO
        // Note: latency and errors will be implemented in phase 2 when we add detailed rule execution tracking
        return RuleMetricsDto.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .ruleType(rule.getRuleType().name())
                .enabled(rule.isEnabled())
                .totalTriggers(totalTriggers)
                .triggersLast24h(triggersLast24h)
                .triggersLast7d(triggersLast7d)
                .avgLatencyMs(0.0)  // TODO: Implement in phase 2
                .maxLatencyMs(0.0)  // TODO: Implement in phase 2
                .minLatencyMs(0.0)  // TODO: Implement in phase 2
                .totalErrors(0L)    // TODO: Implement in phase 2
                .errorsLast24h(0L)  // TODO: Implement in phase 2
                .lastTriggered(lastTriggered)
                .calculatedAt(now)
                .build();
    }

    /**
     * Get metrics for all rules.
     */
    public List<RuleMetricsDto> getAllRulesMetrics() {
        log.info("Fetching metrics for all rules");
        return ruleRepository.findAll().stream()
                .map(rule -> getRuleMetrics(rule.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Convert RuleEntity to RuleDto.
     */
    private RuleDto convertToDto(RuleEntity entity) {
        return RuleDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .ruleType(entity.getRuleType())
                .paramsJson(entity.getParamsJson())
                .enabled(entity.isEnabled())
                .priority(entity.getPriority())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
