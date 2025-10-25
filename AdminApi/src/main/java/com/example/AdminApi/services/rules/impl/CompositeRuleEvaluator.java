package com.example.AdminApi.services.rules.impl;

import com.example.AdminApi.dto.EvaluationContext;
import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.services.rules.RuleEvaluator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluator for COMPOSITE rules.
 *
 * Combines multiple inline conditions with AND/OR logic.
 * Implements short-circuit evaluation for performance.
 *
 * Example rule JSON (AND):
 * {
 *   "operator": "AND",
 *   "conditions": [
 *     {"field": "amount", "operator": ">", "value": 5000},
 *     {"field": "hour", "operator": ">=", "value": 22}
 *   ]
 * }
 *
 * Example rule JSON (OR):
 * {
 *   "operator": "OR",
 *   "conditions": [
 *     {"field": "amount", "operator": ">", "value": 100000},
 *     {"field": "currency", "operator": "!=", "value": "USD"}
 *   ]
 * }
 *
 * Short-circuit behavior:
 * - AND: stops at first false condition
 * - OR: stops at first true condition
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeRuleEvaluator implements RuleEvaluator {

    private final ObjectMapper objectMapper;
    private final ThresholdRuleEvaluator thresholdEvaluator;

    @Override
    public boolean evaluate(TransactionEntity transaction, RuleEntity rule, EvaluationContext context) {
        try {
            JsonNode params = objectMapper.readTree(rule.getParamsJson());

            String operator = params.get("operator").asText();
            JsonNode conditionsNode = params.get("conditions");

            if (!conditionsNode.isArray()) {
                log.warn("Conditions must be an array");
                return false;
            }

            boolean isAnd = "AND".equalsIgnoreCase(operator);
            boolean isOr = "OR".equalsIgnoreCase(operator);

            if (!isAnd && !isOr) {
                log.warn("Unknown operator: {}, expected AND or OR", operator);
                return false;
            }

            // Evaluate conditions with short-circuit
            for (int i = 0; i < conditionsNode.size(); i++) {
                JsonNode condition = conditionsNode.get(i);
                boolean result = evaluateCondition(transaction, condition, context);

                log.debug("Condition[{}] result: {}", i, result);

                // Short-circuit evaluation
                if (isAnd && !result) {
                    // AND: stop at first false
                    log.debug("AND short-circuit: condition {} is false", i);
                    return false;
                }
                if (isOr && result) {
                    // OR: stop at first true
                    log.debug("OR short-circuit: condition {} is true", i);
                    return true;
                }
            }

            // If we get here:
            // - For AND: all conditions were true
            // - For OR: all conditions were false
            return isAnd;

        } catch (Exception e) {
            log.error("Error evaluating COMPOSITE rule: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Evaluates a single condition (inline threshold check).
     *
     * Condition format:
     * {
     *   "field": "amount",
     *   "operator": ">",
     *   "value": 5000
     * }
     *
     * Delegates to ThresholdRuleEvaluator's logic via helper method.
     *
     * @param transaction The transaction
     * @param condition The condition node from JSON
     * @param context Evaluation context
     * @return true if condition passes
     */
    private boolean evaluateCondition(TransactionEntity transaction,
                                       JsonNode condition,
                                       EvaluationContext context) {
        try {
            String field = condition.get("field").asText();
            String operator = condition.get("operator").asText();
            JsonNode valueNode = condition.get("value");

            // Get field value (reuse logic from ThresholdRuleEvaluator)
            Object fieldValue = getFieldValue(transaction, field, context);
            if (fieldValue == null) {
                log.debug("Field '{}' not found or null, condition fails", field);
                return false;
            }

            // Compare based on type
            if (fieldValue instanceof Number) {
                double numValue = ((Number) fieldValue).doubleValue();
                double threshold = valueNode.asDouble();
                return compareNumeric(numValue, operator, threshold);
            } else if (fieldValue instanceof String) {
                String strValue = (String) fieldValue;
                String threshold = valueNode.asText();
                return compareString(strValue, operator, threshold);
            }

            log.warn("Unsupported field value type: {}", fieldValue.getClass().getName());
            return false;

        } catch (Exception e) {
            log.error("Error evaluating condition: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gets field value (copied from ThresholdRuleEvaluator for independence).
     */
    private Object getFieldValue(TransactionEntity transaction, String field, EvaluationContext context) {
        // Handle context fields
        if (field.startsWith("context.")) {
            return getContextFieldValue(transaction, field, context);
        }

        // Handle transaction fields
        switch (field.toLowerCase()) {
            case "amount":
                return transaction.getAmount() != null ? transaction.getAmount().doubleValue() : null;
            case "hour":
                return transaction.getTimestamp() != null ? transaction.getTimestamp().getHour() : null;
            case "dayofweek":
                return transaction.getTimestamp() != null ? transaction.getTimestamp().getDayOfWeek().getValue() : null;
            case "currency":
                return transaction.getCurrency();
            case "sourceid":
                return transaction.getSourceId();
            case "destinationid":
                return transaction.getDestinationId();
            case "geo":
                return transaction.getGeo();
            case "channel":
                return transaction.getChannel();
            default:
                return null;
        }
    }

    /**
     * Gets value from context.
     */
    private Object getContextFieldValue(TransactionEntity transaction, String field, EvaluationContext context) {
        String[] parts = field.split("\\.");
        if (parts.length < 3) {
            return null;
        }

        String contextType = parts[1];
        String contextKey = parts[2];
        String fullKey = contextKey + "_" + transaction.getSourceId();

        if ("counters".equals(contextType)) {
            Integer value = context.getCounters().get(fullKey);
            return value != null ? value : 0;
        } else if ("clientflags".equalsIgnoreCase(contextType)) {
            return context.getClientFlags().get(fullKey);
        }

        return null;
    }

    /**
     * Numeric comparison.
     */
    private boolean compareNumeric(double value, String operator, double threshold) {
        switch (operator) {
            case ">": return value > threshold;
            case "<": return value < threshold;
            case ">=": return value >= threshold;
            case "<=": return value <= threshold;
            case "==": return Math.abs(value - threshold) < 0.001;
            case "!=": return Math.abs(value - threshold) >= 0.001;
            default: return false;
        }
    }

    /**
     * String comparison.
     */
    private boolean compareString(String value, String operator, String threshold) {
        switch (operator) {
            case "==": return value.equals(threshold);
            case "!=": return !value.equals(threshold);
            default: return false;
        }
    }

    @Override
    public String getReason(TransactionEntity transaction, RuleEntity rule, EvaluationContext context) {
        try {
            JsonNode params = objectMapper.readTree(rule.getParamsJson());

            String operator = params.get("operator").asText();
            JsonNode conditionsNode = params.get("conditions");

            List<String> results = new ArrayList<>();
            for (int i = 0; i < conditionsNode.size(); i++) {
                JsonNode condition = conditionsNode.get(i);
                boolean result = evaluateCondition(transaction, condition, context);

                String field = condition.get("field").asText();
                String op = condition.get("operator").asText();
                String value = condition.get("value").asText();

                results.add(String.format("condition[%d]: %s %s %s = %s",
                        i, field, op, value, result ? "PASS" : "FAIL"));
            }

            return String.format("Composite rule (%s): %s", operator, String.join(", ", results));

        } catch (Exception e) {
            return "Error generating reason: " + e.getMessage();
        }
    }

    @Override
    public String getRuleType() {
        return "COMPOSITE";
    }
}
