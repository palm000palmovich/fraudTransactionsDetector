package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of rule engine evaluation.
 *
 * Contains information about whether any rule was triggered,
 * and if so, which rule and why.
 *
 * Used with FIRST-MATCH policy: only the FIRST triggered rule is returned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEngineResult {

    /**
     * Whether any rule was triggered.
     * false = transaction is clean (PROCESSED)
     * true = fraud detected (ALERTED)
     */
    private boolean triggered;

    /**
     * ID of the triggered rule (null if no rules triggered)
     */
    private Long ruleId;

    /**
     * Name of the triggered rule (null if no rules triggered)
     */
    private String ruleName;

    /**
     * Type of the triggered rule (THRESHOLD, PATTERN, COMPOSITE, ML_MODEL)
     */
    private String ruleType;

    /**
     * Human-readable explanation of why the rule triggered.
     * Example: "Amount 15000.00 exceeds threshold 10000.00"
     */
    private String reason;

    /**
     * Creates a result for when no rules were triggered.
     */
    public static RuleEngineResult noMatch() {
        return RuleEngineResult.builder()
                .triggered(false)
                .build();
    }

    /**
     * Creates a result for when a rule was triggered.
     */
    public static RuleEngineResult match(Long ruleId, String ruleName, String ruleType, String reason) {
        return RuleEngineResult.builder()
                .triggered(true)
                .ruleId(ruleId)
                .ruleName(ruleName)
                .ruleType(ruleType)
                .reason(reason)
                .build();
    }
}
