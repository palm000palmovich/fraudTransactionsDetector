package com.example.AdminApi.services.rules.impl;

import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.services.rules.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for COMPOSITE rules.
 *
 * Combines multiple conditions with AND/OR logic.
 *
 * Example rule JSON:
 * {
 *   "condition_type": "AND",
 *   "conditions": [
 *     {"field": "amount", "operator": ">", "value": 5000},
 *     {"field": "geo", "operator": "!=", "value": "US"}
 *   ]
 * }
 *
 * TODO: Phase 6 - Implement composite rule logic
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeRuleEvaluator implements RuleEvaluator {

    @Override
    public boolean evaluate(TransactionEntity transaction, RuleEntity rule) {
        // TODO: Phase 6 - Implement composite rule evaluation
        log.debug("COMPOSITE rule evaluation not yet implemented (ruleId={})", rule.getId());
        return false;
    }

    @Override
    public String getReason(TransactionEntity transaction, RuleEntity rule) {
        return "Composite rule evaluation not yet implemented";
    }

    @Override
    public String getRuleType() {
        return "COMPOSITE";
    }
}
