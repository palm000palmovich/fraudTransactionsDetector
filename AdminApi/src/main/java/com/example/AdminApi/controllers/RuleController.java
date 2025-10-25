package com.example.AdminApi.controllers;

import com.example.AdminApi.dto.CreateRuleDto;
import com.example.AdminApi.dto.RuleDto;
import com.example.AdminApi.dto.RuleMetricsDto;
import com.example.AdminApi.dto.UpdateRuleDto;
import com.example.AdminApi.models.User;
import com.example.AdminApi.services.AuditLogService;
import com.example.AdminApi.services.RuleEngineService;
import com.example.AdminApi.services.RuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing fraud detection rules.
 *
 * Access control:
 * - GET methods: Available to ADMIN and VIEWER roles
 * - POST/PUT/DELETE methods: Available to ADMIN role only
 */
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Slf4j
public class RuleController {

    private final RuleService ruleService;
    private final AuditLogService auditLogService;
    private final RuleEngineService ruleEngineService;

    /**
     * GET /api/rules - Get all rules
     */
    @GetMapping
    public ResponseEntity<List<RuleDto>> getAllRules() {
        log.info("GET /api/rules - Fetching all rules");
        List<RuleDto> rules = ruleService.getAllRules();
        return ResponseEntity.ok(rules);
    }

    /**
     * GET /api/rules/{id} - Get rule by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<RuleDto> getRuleById(@PathVariable Long id) {
        log.info("GET /api/rules/{} - Fetching rule", id);
        RuleDto rule = ruleService.getRuleById(id);
        return ResponseEntity.ok(rule);
    }

    /**
     * POST /api/rules - Create new rule
     */
    @PostMapping
    public ResponseEntity<RuleDto> createRule(@Valid @RequestBody CreateRuleDto dto,
                                              @AuthenticationPrincipal User creator) {
        log.info("POST /api/rules - Creating new rule: {}", dto.getName());
        RuleDto created = ruleService.createRule(dto, creator.getUsername());

        // Hot reload: сразу обновляем кэш правил
        ruleEngineService.reloadRulesCache();
        log.info("Rules cache reloaded after create");

        // Audit log
        String username = getCurrentUsername();
        String details = String.format("Created rule with type=%s, priority=%d", created.getRuleType(), created.getPriority());
        auditLogService.log(username, "CREATE", created.getId(), created.getName(), details);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/rules/{id} - Update existing rule
     */
    @PutMapping("/{id}")
    public ResponseEntity<RuleDto> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRuleDto dto,
            @AuthenticationPrincipal User updater) {
        log.info("PUT /api/rules/{} - Updating rule", id);
        RuleDto updated = ruleService.updateRule(id, dto, updater.getUsername());

        // Hot reload: сразу обновляем кэш правил
        ruleEngineService.reloadRulesCache();
        log.info("Rules cache reloaded after update");

        // Audit log
        String username = getCurrentUsername();
        String details = String.format("Updated rule: type=%s, priority=%d, enabled=%s",
                updated.getRuleType(), updated.getPriority(), updated.getEnabled());
        auditLogService.log(username, "UPDATE", updated.getId(), updated.getName(), details);

        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/rules/{id} - Delete rule
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        log.info("DELETE /api/rules/{} - Deleting rule", id);

        // Get rule info before deletion for audit log
        RuleDto ruleBeforeDelete = ruleService.getRuleById(id);

        ruleService.deleteRule(id);

        // Hot reload: сразу обновляем кэш правил
        ruleEngineService.reloadRulesCache();
        log.info("Rules cache reloaded after delete");

        // Audit log
        String username = getCurrentUsername();
        String details = String.format("Deleted rule: type=%s, priority=%d",
                ruleBeforeDelete.getRuleType(), ruleBeforeDelete.getPriority());
        auditLogService.log(username, "DELETE", id, ruleBeforeDelete.getName(), details);

        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/rules/{id}/toggle - Toggle rule enabled/disabled
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<RuleDto> toggleRule(@PathVariable Long id) {
        log.info("PATCH /api/rules/{}/toggle - Toggling rule", id);
        RuleDto toggled = ruleService.toggleRule(id);

        // Hot reload: сразу обновляем кэш правил
        ruleEngineService.reloadRulesCache();
        log.info("Rules cache reloaded after toggle");

        // Audit log
        String username = getCurrentUsername();
        String action = toggled.getEnabled() ? "ENABLE" : "DISABLE";
        String details = String.format("Rule %s", toggled.getEnabled() ? "enabled" : "disabled");
        auditLogService.log(username, action, toggled.getId(), toggled.getName(), details);

        return ResponseEntity.ok(toggled);
    }

    /**
     * POST /api/rules/reload - Manually reload rules cache
     *
     * Горячая перезагрузка кэша правил без рестарта приложения.
     * Атомарное обновление с copy-on-write паттерном.
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadRulesCache() {
        log.info("POST /api/rules/reload - Manual cache reload requested");

        long startTime = System.currentTimeMillis();

        // Reload cache (атомарное обновление)
        ruleEngineService.reloadRulesCache();

        long duration = System.currentTimeMillis() - startTime;

        // Audit log
        String username = getCurrentUsername();
        auditLogService.log(username, "RELOAD", null, "RulesCache",
                String.format("Manual cache reload completed in %dms", duration));

        // Prepare response
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Rules cache reloaded successfully");
        response.put("durationMs", duration);
        response.put("timestamp", System.currentTimeMillis());

        log.info("Rules cache reloaded manually in {}ms", duration);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/rules/metrics - Get metrics for all rules
     */
    @GetMapping("/metrics")
    public ResponseEntity<List<RuleMetricsDto>> getAllRulesMetrics() {
        log.info("GET /api/rules/metrics - Fetching metrics for all rules");
        List<RuleMetricsDto> metrics = ruleService.getAllRulesMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * GET /api/rules/{id}/metrics - Get metrics for a specific rule
     */
    @GetMapping("/{id}/metrics")
    public ResponseEntity<RuleMetricsDto> getRuleMetrics(@PathVariable Long id) {
        log.info("GET /api/rules/{}/metrics - Fetching metrics for rule", id);
        RuleMetricsDto metrics = ruleService.getRuleMetrics(id);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get current authenticated username
     */
    private String getCurrentUsername() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            log.warn("Failed to get current username", e);
            return "SYSTEM";
        }
    }
}
