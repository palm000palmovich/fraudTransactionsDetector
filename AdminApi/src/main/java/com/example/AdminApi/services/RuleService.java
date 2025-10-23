package com.example.AdminApi.services;

import com.example.AdminApi.dto.CreateRuleDto;
import com.example.AdminApi.dto.RuleDto;
import com.example.AdminApi.dto.UpdateRuleDto;
import com.example.AdminApi.exceptions.ResourceNotFoundException;
import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.repositories.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public RuleDto createRule(CreateRuleDto dto) {
        log.info("Creating new rule: name={}, type={}", dto.getName(), dto.getRuleType());

        RuleEntity rule = new RuleEntity();
        rule.setName(dto.getName());
        rule.setRuleType(dto.getRuleType());
        rule.setParamsJson(dto.getParamsJson());
        rule.setEnabled(dto.getEnabled());
        rule.setPriority(dto.getPriority());
        // version defaults to 1

        RuleEntity saved = ruleRepository.save(rule);
        log.info("Rule created successfully: id={}", saved.getId());

        return convertToDto(saved);
    }

    /**
     * Update existing rule.
     */
    @Transactional
    public RuleDto updateRule(Long id, UpdateRuleDto dto) {
        log.info("Updating rule id={}", id);

        RuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found with id: " + id));

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

        // Increment version
        rule.setVersion(rule.getVersion() + 1);

        RuleEntity updated = ruleRepository.save(rule);
        log.info("Rule updated successfully: id={}, version={}", updated.getId(), updated.getVersion());

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

        return convertToDto(updated);
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
