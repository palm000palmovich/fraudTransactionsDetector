package com.example.AdminApi.services;

import com.example.AdminApi.dto.EvaluationContext;
import com.example.AdminApi.dto.RuleEngineResult;
import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.repositories.RuleRepository;
import com.example.AdminApi.services.rules.RuleEvaluator;
import com.example.AdminApi.services.rules.RuleEvaluatorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule Engine Service - Core fraud detection logic.
 *
 * Implements FIRST-MATCH policy:
 * 1. Load all enabled rules from cache (hot-reloaded every 60s)
 * 2. Sort by priority (ascending: 10, 20, 30...)
 * 3. Evaluate rules one by one until first match
 * 4. Stop immediately when a rule triggers
 * 5. Return result (triggered rule or NO_MATCH)
 *
 * Performance:
 * - Rules cached in memory (copy-on-write pattern for thread safety)
 * - Cache reloaded every 60 seconds via @Scheduled task
 * - No DB query per transaction (only during cache refresh)
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
    private final TransactionHistoryStore historyStore;

    /**
     * Cached rules (copy-on-write: entire list replaced atomically on reload).
     * Volatile ensures visibility across threads without locking on reads.
     */
    private volatile List<RuleEntity> cachedRules = new ArrayList<>();

    /**
     * Load rules cache on application startup.
     */
    @PostConstruct
    public void loadRulesCache() {
        log.info("Loading rules cache on startup...");
        reloadRulesCache();
    }

    /**
     * Reload rules cache every 60 seconds (hot reload).
     * Copy-on-write: creates new list and atomically replaces the old one.
     * Thread-safe: readers continue using old list until new one is ready.
     */
    @Scheduled(fixedRate = 60000)
    public void reloadRulesCache() {
        try {
            List<RuleEntity> freshRules = ruleRepository.findByEnabledTrueOrderByPriorityAsc();

            // Atomic replacement (copy-on-write)
            cachedRules = freshRules;

            log.info("Rules cache reloaded: {} enabled rules", freshRules.size());
        } catch (Exception e) {
            log.error("Failed to reload rules cache: {}", e.getMessage(), e);
            // Keep using old cache on error
        }
    }

    /**
     * Evaluates transaction against all enabled rules using FIRST-MATCH policy.
     *
     * @param transaction The transaction to evaluate
     * @return RuleEngineResult containing triggered rule info or NO_MATCH
     */
    public RuleEngineResult evaluate(TransactionEntity transaction) {
        log.info("Starting rule evaluation for transaction: correlationId={}",
                 transaction.getCorrelationId());

        // Step 1: Get enabled rules from cache (no DB query!)
        List<RuleEntity> enabledRules = cachedRules;

        if (enabledRules.isEmpty()) {
            log.warn("No enabled rules found in cache");
            return RuleEngineResult.noMatch();
        }

        log.info("Evaluating {} cached rules", enabledRules.size());

        // Step 2: Build evaluation context (CRITICAL for Pattern/Composite rules)
        EvaluationContext context = buildContext(transaction);

        // Step 3: Evaluate rules in priority order (FIRST-MATCH)
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

                // Evaluate the rule with context
                boolean triggered = evaluator.evaluate(transaction, rule, context);

                if (triggered) {
                    // FIRST-MATCH: Stop immediately on first triggered rule
                    String reason = evaluator.getReason(transaction, rule, context);

                    log.info("Rule TRIGGERED: id={}, name='{}', type={}, reason={}",
                             rule.getId(), rule.getName(), rule.getRuleType(), reason);

                    // Extract metadata from context (for ML rules, contains scores/thresholds)
                    Map<String, Object> metadata = context.getMetadata();

                    // Return result with metadata if present, otherwise without
                    if (metadata != null && !metadata.isEmpty()) {
                        return RuleEngineResult.matchWithMetadata(
                                rule.getId(),
                                rule.getName(),
                                rule.getRuleType().name(),
                                reason,
                                metadata
                        );
                    } else {
                        return RuleEngineResult.match(
                                rule.getId(),
                                rule.getName(),
                                rule.getRuleType().name(),
                                reason
                        );
                    }
                }

                log.debug("Rule not triggered: id={}, name='{}'", rule.getId(), rule.getName());

            } catch (Exception e) {
                // Log error but continue evaluating other rules
                log.error("Error evaluating rule id={}, name='{}': {}",
                          rule.getId(), rule.getName(), e.getMessage(), e);
            }
        }

        // Step 4: No rules triggered
        log.info("No rules triggered for transaction: correlationId={}",
                 transaction.getCorrelationId());

        return RuleEngineResult.noMatch();
    }

    /**
     * Build evaluation context for the transaction.
     *
     * Context includes:
     * - historyStore: for Pattern rules (sliding window)
     * - clientFlags: for conditional rules (new client, risky, etc.)
     * - counters: for threshold rules (daily count, hourly count)
     * - evaluationCache: for Composite rules (avoid re-computation)
     * - evaluationTime: for time-based rules (night, weekend)
     *
     * @param transaction Current transaction being evaluated
     * @return EvaluationContext with all necessary data
     */
    private EvaluationContext buildContext(TransactionEntity transaction) {
        // TODO: Load clientFlags from DB/cache (e.g., isNew, riskLevel, verified)
        // TODO: Calculate counters from historyStore (e.g., daily/hourly transaction counts)

        return EvaluationContext.builder()
                .historyStore(historyStore)
                .clientFlags(new HashMap<>())     // TODO: Load from DB
                .counters(new HashMap<>())        // TODO: Calculate from history
                .evaluationCache(new HashMap<>()) // Fresh cache for each transaction
                .evaluationTime(LocalDateTime.now())
                .build();
    }
}
