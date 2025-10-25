package com.example.AdminApi.services.rules;

import com.example.AdminApi.dto.EvaluationContext;
import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;

/**
 * Interface for rule evaluators.
 *
 * Each rule type (THRESHOLD, PATTERN, COMPOSITE, ML) has its own evaluator
 * that implements this interface.
 *
 * The RuleEngine uses FIRST-MATCH policy:
 * - Rules are checked in priority order (ascending)
 * - Evaluation stops at the first triggered rule
 * - Only enabled rules are evaluated
 *
 * BREAKING CHANGE (2025-10-24):
 * - Added EvaluationContext parameter to evaluate() and getReason()
 * - Context provides: historyStore, clientFlags, counters, evaluationCache
 * - All existing evaluators must be updated
 */
public interface RuleEvaluator {

    /**
     * Evaluates whether the rule is triggered for the given transaction.
     *
     * @param transaction The transaction to evaluate
     * @param rule The rule to apply
     * @param context Evaluation context (history, flags, counters, cache)
     * @return true if the rule is triggered (fraud detected), false otherwise
     */
    boolean evaluate(TransactionEntity transaction, RuleEntity rule, EvaluationContext context);

    /**
     * Gets the reason why the rule was triggered.
     * This is used to provide context for analysts.
     *
     * @param transaction The transaction that triggered the rule
     * @param rule The rule that was triggered
     * @param context Evaluation context (history, flags, counters, cache)
     * @return Human-readable explanation of why the rule triggered
     */
    String getReason(TransactionEntity transaction, RuleEntity rule, EvaluationContext context);

    /**
     * Returns the rule type that this evaluator handles.
     *
     * @return Rule type (THRESHOLD, PATTERN, COMPOSITE, ML)
     */
    String getRuleType();
}
