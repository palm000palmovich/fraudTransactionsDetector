package com.example.AdminApi.services.rules.impl;

import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.services.rules.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for PATTERN rules.
 *
 * Detects suspicious patterns of behavior like:
 * - Rapid small transfers from same account
 * - Multiple transactions in short time window
 * - Unusual transaction sequences
 *
 * Example rule JSON:
 * {
 *   "pattern_type": "rapid_small_transfers",
 *   "time_window_minutes": 60,
 *   "min_count": 5,
 *   "max_individual_amount": 100
 * }
 *
 * TODO: Phase 6 - Implement pattern detection logic
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatternRuleEvaluator implements RuleEvaluator {

    @Override
    public boolean evaluate(TransactionEntity transaction, RuleEntity rule) {
        // TODO: Phase 6 - Implement pattern detection
        log.debug("PATTERN rule evaluation not yet implemented (ruleId={})", rule.getId());
        return false;
    }

    @Override
    public String getReason(TransactionEntity transaction, RuleEntity rule) {
        return "Pattern detection not yet implemented";
    }

    @Override
    public String getRuleType() {
        return "PATTERN";
    }
}
