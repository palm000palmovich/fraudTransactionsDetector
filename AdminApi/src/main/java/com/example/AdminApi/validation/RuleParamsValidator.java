package com.example.AdminApi.validation;

import com.example.AdminApi.enums.RuleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Validator for rule parameters (JSON).
 *
 * Checks correctness of structure and required fields
 * for each rule type.
 *
 * Usage:
 * - Called before creating/updating rules in RuleService
 * - Returns list of validation errors (empty if valid)
 * - Prevents saving invalid rule configurations
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RuleParamsValidator {

    private final ObjectMapper objectMapper;

    // Valid operators for THRESHOLD and COMPOSITE rules
    private static final List<String> VALID_THRESHOLD_OPERATORS = Arrays.asList(">", "<", ">=", "<=", "==", "!=");
    private static final List<String> VALID_COMPOSITE_OPERATORS = Arrays.asList("AND", "OR");

    // Valid fields for THRESHOLD rules
    private static final List<String> VALID_THRESHOLD_FIELDS = Arrays.asList(
            "amount", "hour", "dayOfWeek", "currency", "sourceId", "destinationId"
    );

    /**
     * Validate rule parameters.
     *
     * @param ruleType Type of the rule
     * @param paramsJson JSON string with parameters
     * @return List of errors (empty if valid)
     */
    public List<String> validate(RuleType ruleType, String paramsJson) {
        List<String> errors = new ArrayList<>();

        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            errors.add("Parameters cannot be empty");
            return errors;
        }

        try {
            JsonNode params = objectMapper.readTree(paramsJson);

            switch (ruleType) {
                case THRESHOLD:
                    validateThreshold(params, errors);
                    break;
                case PATTERN:
                    validatePattern(params, errors);
                    break;
                case COMPOSITE:
                    validateComposite(params, errors);
                    break;
                case ML:
                    validateMl(params, errors);
                    break;
                default:
                    errors.add("Unknown rule type: " + ruleType);
            }

        } catch (Exception e) {
            errors.add("Invalid JSON: " + e.getMessage());
        }

        return errors;
    }

    /**
     * Validate THRESHOLD rule parameters.
     *
     * Required fields:
     * - field: Field to check (amount, hour, etc.)
     * - operator: Comparison operator (>, <, >=, <=, ==, !=)
     * - threshold OR value: Threshold value
     *
     * Optional fields:
     * - contextField: Field from context (counters, clientFlags)
     */
    private void validateThreshold(JsonNode params, List<String> errors) {
        // Required: field
        if (!params.has("field")) {
            errors.add("Missing required field 'field'");
        } else {
            String field = params.get("field").asText();
            // Check if it's a known field or context field
            if (!VALID_THRESHOLD_FIELDS.contains(field) && !field.startsWith("context.")) {
                errors.add("Unknown field '" + field + "'. Valid fields: " +
                        String.join(", ", VALID_THRESHOLD_FIELDS) + ", or 'context.*'");
            }
        }

        // Required: operator
        if (!params.has("operator")) {
            errors.add("Missing required field 'operator'");
        } else {
            String operator = params.get("operator").asText();
            if (!VALID_THRESHOLD_OPERATORS.contains(operator)) {
                errors.add("Invalid operator '" + operator + "'. Valid operators: " +
                        String.join(", ", VALID_THRESHOLD_OPERATORS));
            }
        }

        // Required: threshold or value
        if (!params.has("threshold") && !params.has("value")) {
            errors.add("Missing required field 'threshold' or 'value'");
        }

        // If threshold/value exists, check it's a number (for numeric fields)
        if (params.has("threshold")) {
            JsonNode threshold = params.get("threshold");
            if (!threshold.isNumber() && !threshold.isTextual()) {
                errors.add("Field 'threshold' must be a number or string");
            }
        }
        if (params.has("value")) {
            JsonNode value = params.get("value");
            if (!value.isNumber() && !value.isTextual()) {
                errors.add("Field 'value' must be a number or string");
            }
        }
    }

    /**
     * Validate PATTERN rule parameters.
     *
     * Required fields:
     * - patternType: Type of pattern (e.g., "rapid_small_transfers")
     * - timeWindowMinutes: Time window in minutes
     * - minCount: Minimum number of transactions
     *
     * Optional fields (depends on patternType):
     * - maxAmount: Maximum amount for "small" transactions
     * - groupBy: Grouping key (from, to)
     */
    private void validatePattern(JsonNode params, List<String> errors) {
        // Required: patternType
        if (!params.has("patternType")) {
            errors.add("Missing required field 'patternType'");
        } else {
            String patternType = params.get("patternType").asText();
            // Currently only rapid_small_transfers is implemented
            List<String> validPatterns = Arrays.asList("rapid_small_transfers", "rapid_small");
            if (!validPatterns.contains(patternType)) {
                errors.add("Unknown pattern type '" + patternType + "'. Valid patterns: " +
                        String.join(", ", validPatterns));
            }
        }

        // Required: timeWindowMinutes
        if (!params.has("timeWindowMinutes")) {
            errors.add("Missing required field 'timeWindowMinutes'");
        } else {
            JsonNode timeWindow = params.get("timeWindowMinutes");
            if (!timeWindow.isNumber() || timeWindow.asInt() <= 0) {
                errors.add("Field 'timeWindowMinutes' must be a positive number");
            }
        }

        // Required: minCount
        if (!params.has("minCount")) {
            errors.add("Missing required field 'minCount'");
        } else {
            JsonNode minCount = params.get("minCount");
            if (!minCount.isNumber() || minCount.asInt() <= 0) {
                errors.add("Field 'minCount' must be a positive number");
            }
        }

        // Optional: maxAmount (for rapid_small pattern)
        if (params.has("maxAmount")) {
            JsonNode maxAmount = params.get("maxAmount");
            if (!maxAmount.isNumber() || maxAmount.asDouble() <= 0) {
                errors.add("Field 'maxAmount' must be a positive number");
            }
        }

        // Optional: groupBy
        if (params.has("groupBy")) {
            String groupBy = params.get("groupBy").asText();
            List<String> validGroupBy = Arrays.asList("from", "to", "sourceId", "destinationId");
            if (!validGroupBy.contains(groupBy)) {
                errors.add("Invalid 'groupBy' value '" + groupBy + "'. Valid values: " +
                        String.join(", ", validGroupBy));
            }
        }
    }

    /**
     * Validate COMPOSITE rule parameters.
     *
     * Required fields:
     * - operator: Logical operator (AND, OR)
     * - conditions: Array of inline conditions
     *
     * Each condition must have:
     * - field: Field to check
     * - operator: Comparison operator
     * - value: Value to compare
     */
    private void validateComposite(JsonNode params, List<String> errors) {
        // Required: operator
        if (!params.has("operator")) {
            errors.add("Missing required field 'operator'");
        } else {
            String operator = params.get("operator").asText();
            if (!VALID_COMPOSITE_OPERATORS.contains(operator)) {
                errors.add("Invalid operator '" + operator + "'. Valid operators: " +
                        String.join(", ", VALID_COMPOSITE_OPERATORS));
            }
        }

        // Required: conditions
        if (!params.has("conditions")) {
            errors.add("Missing required field 'conditions'");
        } else {
            JsonNode conditions = params.get("conditions");
            if (!conditions.isArray()) {
                errors.add("Field 'conditions' must be an array");
            } else if (conditions.size() == 0) {
                errors.add("Field 'conditions' must not be empty");
            } else {
                // Validate each condition
                for (int i = 0; i < conditions.size(); i++) {
                    JsonNode condition = conditions.get(i);
                    validateCompositeCondition(condition, i, errors);
                }
            }
        }
    }

    /**
     * Validate a single condition within a COMPOSITE rule.
     */
    private void validateCompositeCondition(JsonNode condition, int index, List<String> errors) {
        String prefix = "Condition[" + index + "]: ";

        // Required: field
        if (!condition.has("field")) {
            errors.add(prefix + "missing required field 'field'");
        }

        // Required: operator
        if (!condition.has("operator")) {
            errors.add(prefix + "missing required field 'operator'");
        } else {
            String operator = condition.get("operator").asText();
            if (!VALID_THRESHOLD_OPERATORS.contains(operator)) {
                errors.add(prefix + "invalid operator '" + operator + "'");
            }
        }

        // Required: value
        if (!condition.has("value")) {
            errors.add(prefix + "missing required field 'value'");
        }
    }

    /**
     * Validate ML rule parameters.
     *
     * Required fields:
     * - threshold: Confidence threshold (0.0 - 1.0)
     *
     * Optional fields:
     * - modelName: Name of the ML model
     */
    private void validateMl(JsonNode params, List<String> errors) {
        // Required: threshold
        if (!params.has("threshold")) {
            errors.add("Missing required field 'threshold'");
        } else {
            JsonNode threshold = params.get("threshold");
            if (!threshold.isNumber()) {
                errors.add("Field 'threshold' must be a number");
            } else {
                double value = threshold.asDouble();
                if (value < 0.0 || value > 1.0) {
                    errors.add("Field 'threshold' must be between 0.0 and 1.0");
                }
            }
        }

        // Optional: modelName
        if (params.has("modelName")) {
            JsonNode modelName = params.get("modelName");
            if (!modelName.isTextual() || modelName.asText().trim().isEmpty()) {
                errors.add("Field 'modelName' must be a non-empty string");
            }
        }
    }
}
