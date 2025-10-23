package com.example.AdminApi.services.rules;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for selecting the appropriate RuleEvaluator based on rule type.
 *
 * This factory maintains a registry of all available evaluators and
 * returns the correct one based on the rule type (THRESHOLD, PATTERN, etc.)
 */
@Slf4j
@Component
public class RuleEvaluatorFactory {

    // Map of rule type -> evaluator
    private final Map<String, RuleEvaluator> evaluators;

    /**
     * Constructor that auto-wires all RuleEvaluator beans and creates a map.
     */
    public RuleEvaluatorFactory(List<RuleEvaluator> evaluatorList) {
        this.evaluators = evaluatorList.stream()
                .collect(Collectors.toMap(
                        RuleEvaluator::getRuleType,
                        Function.identity()
                ));

        log.info("Registered {} rule evaluators: {}",
                 evaluators.size(),
                 evaluators.keySet());
    }

    /**
     * Gets the evaluator for the specified rule type.
     *
     * @param ruleType The rule type (THRESHOLD, PATTERN, COMPOSITE, ML_MODEL)
     * @return The evaluator for that type, or null if not found
     */
    public RuleEvaluator getEvaluator(String ruleType) {
        RuleEvaluator evaluator = evaluators.get(ruleType);

        if (evaluator == null) {
            log.warn("No evaluator found for rule type: {}", ruleType);
        }

        return evaluator;
    }

    /**
     * Checks if an evaluator exists for the given rule type.
     */
    public boolean hasEvaluator(String ruleType) {
        return evaluators.containsKey(ruleType);
    }
}
