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

import java.math.BigDecimal;
import java.time.DayOfWeek;

/**
 * Evaluator for THRESHOLD rules.
 *
 * Checks if a transaction field exceeds a threshold value.
 *
 * Supported fields:
 * - Transaction fields: amount, hour, dayOfWeek, currency, sourceId, destinationId
 * - Context fields: context.counters.*, context.clientFlags.*
 *
 * Example rule JSON:
 * {
 *   "field": "amount",
 *   "operator": ">",
 *   "threshold": 10000
 * }
 *
 * Example with hour:
 * {
 *   "field": "hour",
 *   "operator": ">=",
 *   "threshold": 22
 * }
 *
 * Example with context:
 * {
 *   "field": "context.counters.daily",
 *   "operator": ">",
 *   "threshold": 10
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
    public boolean evaluate(TransactionEntity transaction, RuleEntity rule, EvaluationContext context) {
        try {
            JsonNode params = objectMapper.readTree(rule.getParamsJson());

            String field = params.get("field").asText();
            String operator = params.get("operator").asText();

            // Threshold can be either "threshold" or "value" key
            JsonNode thresholdNode = params.has("threshold") ? params.get("threshold") : params.get("value");

            // Get field value from transaction or context
            Object fieldValue = getFieldValue(transaction, field, context);
            if (fieldValue == null) {
                log.warn("Field '{}' not found or null", field);
                return false;
            }

            // Handle numeric comparison
            if (fieldValue instanceof Number) {
                double numValue = ((Number) fieldValue).doubleValue();
                double threshold = thresholdNode.asDouble();
                return compareNumeric(numValue, operator, threshold);
            }

            // Handle string comparison
            if (fieldValue instanceof String) {
                String strValue = (String) fieldValue;
                String threshold = thresholdNode.asText();
                return compareString(strValue, operator, threshold);
            }

            log.warn("Unsupported field value type: {}", fieldValue.getClass().getName());
            return false;

        } catch (Exception e) {
            log.error("Error evaluating THRESHOLD rule: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String getReason(TransactionEntity transaction, RuleEntity rule, EvaluationContext context) {
        try {
            JsonNode params = objectMapper.readTree(rule.getParamsJson());

            String field = params.get("field").asText();
            String operator = params.get("operator").asText();
            JsonNode thresholdNode = params.has("threshold") ? params.get("threshold") : params.get("value");

            Object fieldValue = getFieldValue(transaction, field, context);

            if (fieldValue instanceof Number) {
                return String.format("Field '%s' value %.2f %s threshold %.2f",
                        field, ((Number) fieldValue).doubleValue(), operator, thresholdNode.asDouble());
            } else if (fieldValue instanceof String) {
                return String.format("Field '%s' value '%s' %s threshold '%s'",
                        field, fieldValue, operator, thresholdNode.asText());
            } else {
                return String.format("Field '%s' %s threshold %s", field, operator, thresholdNode.asText());
            }

        } catch (Exception e) {
            return "Error generating reason: " + e.getMessage();
        }
    }

    @Override
    public String getRuleType() {
        return "THRESHOLD";
    }

    /**
     * Gets field value from transaction or context.
     *
     * Supported fields:
     * - Transaction fields: amount, hour, dayOfWeek, currency, sourceId, destinationId
     * - Context fields: context.counters.*, context.clientFlags.*
     *
     * @param transaction The transaction
     * @param field Field name (e.g., "amount", "hour", "context.counters.daily")
     * @param context Evaluation context
     * @return Field value as Object (Number or String)
     */
    private Object getFieldValue(TransactionEntity transaction, String field, EvaluationContext context) {
        // Handle context fields
        if (field.startsWith("context.")) {
            return getContextFieldValue(transaction, field, context);
        }

        // Handle transaction fields
        switch (field.toLowerCase()) {
            case "amount":
                return transaction.getAmount() != null
                        ? transaction.getAmount().doubleValue()
                        : null;

            case "hour":
                // Extract hour from timestamp (0-23)
                return transaction.getTimestamp() != null
                        ? transaction.getTimestamp().getHour()
                        : null;

            case "dayofweek":
                // Extract day of week from timestamp (1=Monday, 7=Sunday)
                return transaction.getTimestamp() != null
                        ? transaction.getTimestamp().getDayOfWeek().getValue()
                        : null;

            case "currency":
                return transaction.getCurrency();

            case "sourceid":
                return transaction.getSourceId();

            case "destinationid":
                return transaction.getDestinationId();

            default:
                log.warn("Unknown field: {}", field);
                return null;
        }
    }

    /**
     * Gets value from context (counters or clientFlags).
     *
     * Examples:
     * - "context.counters.daily" -> context.getCounters().get("daily_ACC001")
     * - "context.clientFlags.isNew" -> context.getClientFlags().get("isNew_ACC001")
     *
     * @param transaction The transaction (for constructing key with sourceId)
     * @param field Full field name (e.g., "context.counters.daily")
     * @param context Evaluation context
     * @return Value from context or null
     */
    private Object getContextFieldValue(TransactionEntity transaction, String field, EvaluationContext context) {
        String[] parts = field.split("\\.");
        if (parts.length < 3) {
            log.warn("Invalid context field format: {}", field);
            return null;
        }

        String contextType = parts[1]; // "counters" or "clientFlags"
        String contextKey = parts[2];  // "daily", "isNew", etc.

        // Construct full key with sourceId (e.g., "daily_ACC001")
        String fullKey = contextKey + "_" + transaction.getSourceId();

        if ("counters".equals(contextType)) {
            Integer value = context.getCounters().get(fullKey);
            return value != null ? value : 0; // Default to 0 if not found
        } else if ("clientflags".equalsIgnoreCase(contextType)) {
            return context.getClientFlags().get(fullKey);
        } else {
            log.warn("Unknown context type: {}", contextType);
            return null;
        }
    }

    /**
     * Compares two numeric values using the specified operator.
     */
    private boolean compareNumeric(double value, String operator, double threshold) {
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

    /**
     * Compares two string values using the specified operator.
     *
     * Supported operators: ==, !=
     * Other operators (>, <, >=, <=) not supported for strings.
     */
    private boolean compareString(String value, String operator, String threshold) {
        switch (operator) {
            case "==":
                return value.equals(threshold);
            case "!=":
                return !value.equals(threshold);
            default:
                log.warn("Operator '{}' not supported for string comparison", operator);
                return false;
        }
    }
}
