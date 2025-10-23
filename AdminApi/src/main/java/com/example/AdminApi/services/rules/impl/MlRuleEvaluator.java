package com.example.AdminApi.services.rules.impl;

import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.services.rules.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for ML_MODEL rules.
 *
 * Uses machine learning models to detect fraud.
 *
 * Example rule JSON:
 * {
 *   "model_name": "fraud_detector_v1",
 *   "threshold_score": 0.85
 * }
 *
 * TODO: Phase 6 - Integrate with ML model service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MlRuleEvaluator implements RuleEvaluator {

    @Override
    public boolean evaluate(TransactionEntity transaction, RuleEntity rule) {
        // TODO: Phase 6 - Implement ML model integration
        log.debug("ML_MODEL rule evaluation not yet implemented (ruleId={})", rule.getId());
        return false;
    }

    @Override
    public String getReason(TransactionEntity transaction, RuleEntity rule) {
        return "ML model evaluation not yet implemented";
    }

    @Override
    public String getRuleType() {
        return "ML";
    }
}
