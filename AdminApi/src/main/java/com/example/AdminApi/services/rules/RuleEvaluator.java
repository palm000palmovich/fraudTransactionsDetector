package com.example.AdminApi.services.rules;

import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;

/**
 * Interface for rule evaluators.
 *
 * Each rule type (THRESHOLD, PATTERN, COMPOSITE, ML_MODEL) has its own evaluator
 * that implements this interface.
 *
 * The RuleEngine uses FIRST-MATCH policy:
 * - Rules are checked in priority order (ascending)
 * - Evaluation stops at the first triggered rule
 * - Only enabled rules are evaluated
 */
public interface RuleEvaluator {

    /**
     * Evaluates whether the rule is triggered for the given transaction.
     *
     * @param transaction The transaction to evaluate
     * @param rule The rule to apply
     * @return true if the rule is triggered (fraud detected), false otherwise
     */
    boolean evaluate(TransactionEntity transaction, RuleEntity rule);

    /**
     * Gets the reason why the rule was triggered.
     * This is used to provide context for analysts.
     *
     * @param transaction The transaction that triggered the rule
     * @param rule The rule that was triggered
     * @return Human-readable explanation of why the rule triggered
     */
    String getReason(TransactionEntity transaction, RuleEntity rule);

    /**
     * Returns the rule type that this evaluator handles.
     *
     * @return Rule type (THRESHOLD, PATTERN, COMPOSITE, ML_MODEL)
     */
    String getRuleType();
}
