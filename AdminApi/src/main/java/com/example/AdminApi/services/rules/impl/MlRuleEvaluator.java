package com.example.AdminApi.services.rules.impl;

import com.example.AdminApi.dto.EvaluationContext;
import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.services.ml.FeatureBuilder;
import com.example.AdminApi.services.ml.OnnxModelService;
import com.example.AdminApi.services.rules.RuleEvaluator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Evaluator for ML_MODEL rules.
 *
 * Использует предобученную ONNX модель для детекции мошенничества.
 *
 * Процесс:
 * 1. Извлекаем features из транзакции и контекста (FeatureBuilder)
 * 2. Выполняем inference через ONNX Runtime (OnnxModelService)
 * 3. Сравниваем score с threshold
 * 4. Логируем результат для объяснимости
 *
 * Важно:
 * - Мягкий fallback при ошибках (не ломает конвейер)
 * - Логирование score, threshold, modelVersion, features
 * - Все данные из RAM (TransactionHistoryStore), не из БД!
 *
 * Performance: ~15ms total (5ms features + 10ms inference)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MlRuleEvaluator implements RuleEvaluator {

    private final FeatureBuilder featureBuilder;
    private final OnnxModelService modelService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean evaluate(TransactionEntity transaction, RuleEntity rule, EvaluationContext context) {
        String fallbackAction = "PASS"; // default
        double threshold = 0.5; // default

        try {
            // Parse parameters
            JsonNode params = objectMapper.readTree(rule.getParamsJson());
            threshold = params.has("threshold") ? params.get("threshold").asDouble() : 0.5;
            fallbackAction = params.has("fallbackAction") ? params.get("fallbackAction").asText() : "PASS";

            String modelVersion = modelService.getModelVersion();

            log.info("ML Rule evaluation started: ruleId={}, ruleName={}, threshold={}, fallbackAction={}, " +
                    "txId={}, from={}, to={}, amount={}, channel={}",
                rule.getId(), rule.getName(), threshold, fallbackAction,
                transaction.getCorrelationId(), transaction.getSourceId(), transaction.getDestinationId(),
                transaction.getAmount(), transaction.getChannel());

            // Build features (from RAM - TransactionHistoryStore)
            float[] features;
            long featureTime;
            try {
                long featureStartTime = System.currentTimeMillis();
                features = featureBuilder.buildFeatures(transaction, context);
                featureTime = System.currentTimeMillis() - featureStartTime;

                log.info("Features built in {}ms for tx {}: amount={}, type={}, location={}, velocity={}, deviation={}",
                    featureTime, transaction.getCorrelationId(),
                    features[0], (int)features[1], (int)features[3], (int)features[7], features[6]);
            } catch (Exception e) {
                log.error("Feature building failed for ruleId={}, tx={}, from={}, to={}, amount={}: {}",
                    rule.getId(), transaction.getCorrelationId(), transaction.getSourceId(),
                    transaction.getDestinationId(), transaction.getAmount(), e.getMessage(), e);
                return fallbackResult(fallbackAction, "feature_building_error", transaction);
            }

            // Validate features before inference
            if (features == null) {
                log.error("Features are null for tx {}", transaction.getCorrelationId());
                return fallbackResult(fallbackAction, "features_null", transaction);
            }

            if (features.length != 15) {
                log.error("Invalid feature count: expected 15, got {} for tx {}",
                    features.length, transaction.getCorrelationId());
                return fallbackResult(fallbackAction, "invalid_feature_count", transaction);
            }

            // Check for NaN/Inf values
            for (int i = 0; i < features.length; i++) {
                if (Float.isNaN(features[i]) || Float.isInfinite(features[i])) {
                    log.error("Invalid feature value at index {}: {} for tx {}",
                        i, features[i], transaction.getCorrelationId());
                    return fallbackResult(fallbackAction, "invalid_feature_value", transaction);
                }
            }

            // Inference (ONNX model)
            float score;
            long inferenceTime;
            try {
                long inferenceStartTime = System.currentTimeMillis();
                score = modelService.predict(features);
                inferenceTime = System.currentTimeMillis() - inferenceStartTime;

                log.info("ML inference completed in {}ms for tx {}: score={}",
                    inferenceTime, transaction.getCorrelationId(), score);
            } catch (Exception e) {
                log.error("ML inference failed for ruleId={}, tx={}, from={}, to={}, amount={}, modelVersion={}: {}",
                    rule.getId(), transaction.getCorrelationId(), transaction.getSourceId(),
                    transaction.getDestinationId(), transaction.getAmount(), modelVersion, e.getMessage(), e);
                return fallbackResult(fallbackAction, "ml_inference_error", transaction);
            }

            // Save ML results to context metadata for getReason() and UI display
            context.getMetadata().put("ml_score", score);
            context.getMetadata().put("ml_threshold", threshold);
            context.getMetadata().put("ml_model_version", modelVersion);

            // Compare with threshold
            boolean triggered = score >= threshold;

            log.info("ML Rule result: score={}, threshold={}, triggered={}, modelVersion={}, " +
                    "featureTime={}ms, inferenceTime={}ms, totalTime={}ms, tx={}, from={}, to={}, amount={}, channel={}",
                score, threshold, triggered, modelVersion,
                featureTime, inferenceTime, (featureTime + inferenceTime),
                transaction.getCorrelationId(), transaction.getSourceId(),
                transaction.getDestinationId(), transaction.getAmount(), transaction.getChannel());

            // Log features for explainability (ALWAYS for debugging ML scores)
            logFeaturesExplained(features, transaction);

            return triggered;

        } catch (Exception e) {
            // Catch-all for any other unexpected errors (e.g., JSON parsing)
            log.error("Unexpected error in ML rule evaluation for ruleId={}, tx={}, from={}, to={}, amount={}: {}",
                rule.getId(), transaction.getCorrelationId(), transaction.getSourceId(),
                transaction.getDestinationId(), transaction.getAmount(), e.getMessage(), e);

            return fallbackResult(fallbackAction, "unexpected_error", transaction);
        }
    }

    /**
     * Graceful degradation: возвращает fallback результат при ошибках.
     *
     * @param action "PASS" или "ALERTED"
     * @param reason причина ошибки для логирования
     * @param tx транзакция
     * @return triggered=false для PASS, triggered=true для ALERTED
     */
    private boolean fallbackResult(String action, String reason, TransactionEntity tx) {
        boolean triggered = "ALERTED".equalsIgnoreCase(action);

        log.warn("ML rule fallback triggered for tx={}, from={}, to={}, amount={}, channel={}: " +
                "action={}, reason={}, result={}",
            tx.getCorrelationId(), tx.getSourceId(), tx.getDestinationId(),
            tx.getAmount(), tx.getChannel(), action, reason,
            triggered ? "ALERTED" : "PASS");

        return triggered;
    }

    @Override
    public String getReason(TransactionEntity transaction, RuleEntity rule, EvaluationContext context) {
        // Get ML results from context metadata
        Float score = (Float) context.getMetadata().get("ml_score");
        Double threshold = (Double) context.getMetadata().get("ml_threshold");
        String modelVersion = (String) context.getMetadata().get("ml_model_version");

        if (score == null) {
            return "ML model evaluation not available";
        }

        // Format: "ML fraud score: 0.9234 (threshold: 0.90, model: v0.5)"
        return String.format(
            "ML fraud score: %.4f (threshold: %.2f, model: %s)",
            score,
            threshold != null ? threshold : 0.5,
            modelVersion != null ? modelVersion : "unknown"
        );
    }

    @Override
    public String getRuleType() {
        return "ML";
    }

    /**
     * Логирует features с объяснениями для отладки и объяснимости.
     */
    private void logFeaturesExplained(float[] features, TransactionEntity tx) {
        log.info("Feature breakdown for tx {}:", tx.getCorrelationId());
        log.info("  [0] amount: {}", features[0]);
        log.info("  [1] transaction_type: {} ({})", features[1], getTransactionTypeLabel((int)features[1]));
        log.info("  [2] merchant_category: {} (STUB)", features[2]);
        log.info("  [3] location: {} ({})", features[3], tx.getGeo());
        log.info("  [4] device_used: {} (STUB)", features[4]);
        log.info("  [5] time_since_last: {}s", features[5]);
        log.info("  [6] spending_deviation: {}", features[6]);
        log.info("  [7] velocity: {} tx/10min", (int)features[7]);
        log.info("  [8] payment_channel: {} ({})", features[8], tx.getChannel());
        log.info("  [9] amount_on_time: {}", features[9]);
    }

    private String getTransactionTypeLabel(int type) {
        return switch (type) {
            case 0 -> "deposit";
            case 1 -> "payment";
            case 2 -> "transfer";
            case 3 -> "withdrawal";
            default -> "unknown";
        };
    }
}
