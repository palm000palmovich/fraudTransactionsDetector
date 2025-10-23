package com.example.AdminApi.controllers;

import com.example.AdminApi.dto.CreateRuleDto;
import com.example.AdminApi.dto.RuleDto;
import com.example.AdminApi.dto.UpdateRuleDto;
import com.example.AdminApi.services.RuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing fraud detection rules (Admin Panel).
 */
@RestController
@RequestMapping("/api/admin/rules")
@RequiredArgsConstructor
@Slf4j
public class RuleController {

    private final RuleService ruleService;

    /**
     * GET /api/admin/rules - Get all rules
     */
    @GetMapping
    public ResponseEntity<List<RuleDto>> getAllRules() {
        log.info("GET /api/admin/rules - Fetching all rules");
        List<RuleDto> rules = ruleService.getAllRules();
        return ResponseEntity.ok(rules);
    }

    /**
     * GET /api/admin/rules/{id} - Get rule by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<RuleDto> getRuleById(@PathVariable Long id) {
        log.info("GET /api/admin/rules/{} - Fetching rule", id);
        RuleDto rule = ruleService.getRuleById(id);
        return ResponseEntity.ok(rule);
    }

    /**
     * POST /api/admin/rules - Create new rule
     */
    @PostMapping
    public ResponseEntity<RuleDto> createRule(@Valid @RequestBody CreateRuleDto dto) {
        log.info("POST /api/admin/rules - Creating new rule: {}", dto.getName());
        RuleDto created = ruleService.createRule(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/admin/rules/{id} - Update existing rule
     */
    @PutMapping("/{id}")
    public ResponseEntity<RuleDto> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRuleDto dto) {
        log.info("PUT /api/admin/rules/{} - Updating rule", id);
        RuleDto updated = ruleService.updateRule(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/admin/rules/{id} - Delete rule
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        log.info("DELETE /api/admin/rules/{} - Deleting rule", id);
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/admin/rules/{id}/toggle - Toggle rule enabled/disabled
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<RuleDto> toggleRule(@PathVariable Long id) {
        log.info("PATCH /api/admin/rules/{}/toggle - Toggling rule", id);
        RuleDto toggled = ruleService.toggleRule(id);
        return ResponseEntity.ok(toggled);
    }
}
