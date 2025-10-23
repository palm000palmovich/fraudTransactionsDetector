package com.example.AdminApi.services.rules.impl;

import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.services.rules.RuleEvaluator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Evaluator for THRESHOLD rules.
 *
 * Checks if a transaction field exceeds a threshold value.
 *
 * Example rule JSON:
 * {
 *   "field": "amount",
 *   "operator": ">",
 *   "threshold": 10000
 * }
 *
 * Supported operators: >, <, >=, <=, ==, !=
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdRuleEvaluator implements RuleEvaluator {

    private final ObjectMapper objectMapper;

    @Override
    public boolean evaluate(TransactionEntity transaction, RuleEntity rule) {
        try {
            JsonNode params = objectMapper.readTree(rule.getParamsJson());

            String field = params.get("field").asText();
            String operator = params.get("operator").asText();
            double threshold = params.get("threshold").asDouble();

            // Get field value from transaction
            Double fieldValue = getFieldValue(transaction, field);
            if (fieldValue == null) {
                log.warn("Field '{}' not found or null in transaction", field);
                return false;
            }

            // Compare using operator
            return compare(fieldValue, operator, threshold);

        } catch (Exception e) {
            log.error("Error evaluating THRESHOLD rule: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String getReason(TransactionEntity transaction, RuleEntity rule) {
        try {
            JsonNode params = objectMapper.readTree(rule.getParamsJson());

            String field = params.get("field").asText();
            String operator = params.get("operator").asText();
            double threshold = params.get("threshold").asDouble();
            Double fieldValue = getFieldValue(transaction, field);

            return String.format("Field '%s' value %.2f %s threshold %.2f",
                                 field, fieldValue, operator, threshold);

        } catch (Exception e) {
            return "Error generating reason: " + e.getMessage();
        }
    }

    @Override
    public String getRuleType() {
        return "THRESHOLD";
    }

    /**
     * Gets field value from transaction by field name.
     */
    private Double getFieldValue(TransactionEntity transaction, String field) {
        switch (field.toLowerCase()) {
            case "amount":
                return transaction.getAmount() != null
                    ? transaction.getAmount().doubleValue()
                    : null;
            default:
                log.warn("Unknown field: {}", field);
                return null;
        }
    }

    /**
     * Compares two values using the specified operator.
     */
    private boolean compare(double value, String operator, double threshold) {
        switch (operator) {
            case ">":
                return value > threshold;
            case "<":
                return value < threshold;
            case ">=":
                return value >= threshold;
            case "<=":
                return value <= threshold;
            case "==":
                return Math.abs(value - threshold) < 0.001; // Float comparison tolerance
            case "!=":
                return Math.abs(value - threshold) >= 0.001;
            default:
                log.warn("Unknown operator: {}", operator);
                return false;
        }
    }
}
