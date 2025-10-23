package com.example.AdminApi.services;

import com.example.AdminApi.dto.RuleEngineResult;
import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.repositories.RuleRepository;
import com.example.AdminApi.services.rules.RuleEvaluator;
import com.example.AdminApi.services.rules.RuleEvaluatorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rule Engine Service - Core fraud detection logic.
 *
 * Implements FIRST-MATCH policy:
 * 1. Load all enabled rules from database
 * 2. Sort by priority (ascending: 10, 20, 30...)
 * 3. Evaluate rules one by one until first match
 * 4. Stop immediately when a rule triggers
 * 5. Return result (triggered rule or NO_MATCH)
 *
 * Integration:
 * - Called by TransactionProcessingService after transaction is saved to DB
 * - Returns RuleEngineResult with triggered rule info (if any)
 * - Transaction status updated to ALERTED if rule triggered
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RuleEngineService {

    private final RuleRepository ruleRepository;
    private final RuleEvaluatorFactory evaluatorFactory;

    /**
     * Evaluates transaction against all enabled rules using FIRST-MATCH policy.
     *
     * @param transaction The transaction to evaluate
     * @return RuleEngineResult containing triggered rule info or NO_MATCH
     */
    public RuleEngineResult evaluate(TransactionEntity transaction) {
        log.info("Starting rule evaluation for transaction: correlationId={}",
                 transaction.getCorrelationId());

        // Step 1: Load all enabled rules, sorted by priority (FIRST-MATCH)
        List<RuleEntity> enabledRules = ruleRepository.findByEnabledTrueOrderByPriorityAsc();

        if (enabledRules.isEmpty()) {
            log.warn("No enabled rules found in database");
            return RuleEngineResult.noMatch();
        }

        log.info("Found {} enabled rules to evaluate", enabledRules.size());

        // Step 2: Evaluate rules in priority order (FIRST-MATCH)
        for (RuleEntity rule : enabledRules) {
            try {
                // Get the appropriate evaluator for this rule type
                RuleEvaluator evaluator = evaluatorFactory.getEvaluator(rule.getRuleType().name());

                if (evaluator == null) {
                    log.warn("No evaluator found for rule type: {} (ruleId={})",
                             rule.getRuleType(), rule.getId());
                    continue; // Skip this rule and try next one
                }

                log.debug("Evaluating rule: id={}, name='{}', type={}, priority={}",
                          rule.getId(), rule.getName(), rule.getRuleType(), rule.getPriority());

                // Evaluate the rule
                boolean triggered = evaluator.evaluate(transaction, rule);

                if (triggered) {
                    // FIRST-MATCH: Stop immediately on first triggered rule
                    String reason = evaluator.getReason(transaction, rule);

                    log.info("Rule TRIGGERED: id={}, name='{}', type={}, reason={}",
                             rule.getId(), rule.getName(), rule.getRuleType(), reason);

                    return RuleEngineResult.match(
                            rule.getId(),
                            rule.getName(),
                            rule.getRuleType().name(),
                            reason
                    );
                }

                log.debug("Rule not triggered: id={}, name='{}'", rule.getId(), rule.getName());

            } catch (Exception e) {
                // Log error but continue evaluating other rules
                log.error("Error evaluating rule id={}, name='{}': {}",
                          rule.getId(), rule.getName(), e.getMessage(), e);
            }
        }

        // Step 3: No rules triggered
        log.info("No rules triggered for transaction: correlationId={}",
                 transaction.getCorrelationId());

        return RuleEngineResult.noMatch();
    }
}
