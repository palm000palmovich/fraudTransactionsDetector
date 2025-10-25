package com.example.AdminApi.services.rules.impl;

import com.example.AdminApi.dto.EvaluationContext;
import com.example.AdminApi.dto.TransactionRecord;
import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.services.rules.RuleEvaluator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Evaluator for PATTERN rules.
 *
 * Detects suspicious patterns of behavior using transaction history.
 * Uses sliding window approach with TransactionHistoryStore.
 *
 * Supported patterns:
 * - rapid_small_transfers: Many small transactions in short time window
 *
 * Example rule JSON:
 * {
 *   "patternType": "rapid_small_transfers",
 *   "timeWindowMinutes": 10,
 *   "minCount": 5,
 *   "maxAmount": 500,
 *   "groupBy": "from"
 * }
 *
 * This would trigger if:
 * - 5 or more transactions
 * - Each transaction <= 500
 * - All within 10 minutes
 * - From same sourceId
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatternRuleEvaluator implements RuleEvaluator {

    private final ObjectMapper objectMapper;

    @Override
    public boolean evaluate(TransactionEntity transaction, RuleEntity rule, EvaluationContext context) {
        try {
            JsonNode params = objectMapper.readTree(rule.getParamsJson());

            String patternType = params.get("patternType").asText();

            // Currently only rapid_small_transfers is implemented
            if ("rapid_small_transfers".equalsIgnoreCase(patternType) ||
                "rapid_small".equalsIgnoreCase(patternType)) {
                return evaluateRapidSmallTransfers(transaction, params, context);
            }

            log.warn("Unknown pattern type: {}", patternType);
            return false;

        } catch (Exception e) {
            log.error("Error evaluating PATTERN rule: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Evaluates rapid_small_transfers pattern.
     *
     * Detects when multiple small transactions occur in a short time window.
     * Common fraud pattern: breaking large transfer into many small ones.
     *
     * @param transaction Current transaction
     * @param params Rule parameters
     * @param context Evaluation context with history store
     * @return true if pattern detected (fraud suspected)
     */
    private boolean evaluateRapidSmallTransfers(TransactionEntity transaction,
                                                 JsonNode params,
                                                 EvaluationContext context) {
        // Parse parameters
        int timeWindowMinutes = params.get("timeWindowMinutes").asInt();
        int minCount = params.get("minCount").asInt();
        double maxAmount = params.has("maxAmount") ? params.get("maxAmount").asDouble() : Double.MAX_VALUE;
        String groupBy = params.has("groupBy") ? params.get("groupBy").asText() : "from";

        log.info("PATTERN RULE DEBUG: timeWindowMinutes={}, minCount={}, maxAmount={}, groupBy={}",
                timeWindowMinutes, minCount, maxAmount, groupBy);

        // Determine grouping key (sourceId or destinationId)
        String groupKey;
        if ("to".equalsIgnoreCase(groupBy) || "destinationId".equalsIgnoreCase(groupBy)) {
            groupKey = transaction.getDestinationId();
        } else {
            groupKey = transaction.getSourceId(); // default: group by sender
        }

        log.info("PATTERN RULE DEBUG: groupKey={}, transaction.sourceId={}, transaction.destinationId={}, transaction.timestamp={}",
                groupKey, transaction.getSourceId(), transaction.getDestinationId(), transaction.getTimestamp());

        // Get transactions from history store within time window
        // ВАЖНО: используем timestamp текущей транзакции как referenceTime для правильного расчета окна
        Duration window = Duration.ofMinutes(timeWindowMinutes);
        List<TransactionRecord> recentTransactions = context.getHistoryStore()
                .getInWindow(groupKey, window, transaction.getTimestamp());

        log.info("PATTERN RULE DEBUG: Found {} transactions in window for groupKey={}",
                recentTransactions.size(), groupKey);

        // Filter: keep only transactions <= maxAmount
        long smallTransactionsCount = recentTransactions.stream()
                .filter(t -> t.getAmount().doubleValue() <= maxAmount)
                .count();

        log.info("PATTERN RULE DEBUG: {} small transactions (<= {}) after filtering, minCount threshold={}",
                smallTransactionsCount, maxAmount, minCount);

        // Check if count exceeds threshold
        boolean triggered = smallTransactionsCount >= minCount;

        if (triggered) {
            log.info("Pattern detected: {} small transactions (<= {}) in {} minutes from {}",
                    smallTransactionsCount, maxAmount, timeWindowMinutes, groupKey);
        } else {
            log.info("Pattern NOT detected: {} small transactions (<= {}) in {} minutes from {} (need >= {})",
                    smallTransactionsCount, maxAmount, timeWindowMinutes, groupKey, minCount);
        }

        return triggered;
    }

    @Override
    public String getReason(TransactionEntity transaction, RuleEntity rule, EvaluationContext context) {
        try {
            JsonNode params = objectMapper.readTree(rule.getParamsJson());

            String patternType = params.get("patternType").asText();

            if ("rapid_small_transfers".equalsIgnoreCase(patternType) ||
                "rapid_small".equalsIgnoreCase(patternType)) {
                return getRapidSmallTransfersReason(transaction, params, context);
            }

            return "Unknown pattern type: " + patternType;

        } catch (Exception e) {
            return "Error generating reason: " + e.getMessage();
        }
    }

    /**
     * Generates reason message for rapid_small_transfers pattern.
     */
    private String getRapidSmallTransfersReason(TransactionEntity transaction,
                                                 JsonNode params,
                                                 EvaluationContext context) {
        int timeWindowMinutes = params.get("timeWindowMinutes").asInt();
        int minCount = params.get("minCount").asInt();
        double maxAmount = params.has("maxAmount") ? params.get("maxAmount").asDouble() : Double.MAX_VALUE;
        String groupBy = params.has("groupBy") ? params.get("groupBy").asText() : "from";

        String groupKey = "to".equalsIgnoreCase(groupBy) || "destinationId".equalsIgnoreCase(groupBy)
                ? transaction.getDestinationId()
                : transaction.getSourceId();

        Duration window = Duration.ofMinutes(timeWindowMinutes);
        List<TransactionRecord> recentTransactions = context.getHistoryStore()
                .getInWindow(groupKey, window, transaction.getTimestamp());

        long smallTransactionsCount = recentTransactions.stream()
                .filter(t -> t.getAmount().doubleValue() <= maxAmount)
                .count();

        BigDecimal totalAmount = recentTransactions.stream()
                .filter(t -> t.getAmount().doubleValue() <= maxAmount)
                .map(TransactionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return String.format("Rapid small transfers detected: %d transactions (>= %d threshold) of <= %.2f within %d minutes from %s=%s, total amount: %.2f",
                smallTransactionsCount, minCount, maxAmount, timeWindowMinutes,
                groupBy, groupKey, totalAmount.doubleValue());
    }

    @Override
    public String getRuleType() {
        return "PATTERN";
    }
}
