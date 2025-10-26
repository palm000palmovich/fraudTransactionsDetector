package com.example.AdminApi.ruleParametres;

import com.example.AdminApi.enums.RuleType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Параметры для ML-правила.
 *
 * Пример JSON в paramsJson:
 * {
 *   "threshold": 0.8,
 *   "modelVersion": "production-15f",
 *   "fallbackAction": "PASS"
 * }
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MlRuleParams extends RuleParams {

    /**
     * Порог срабатывания (0.0 - 1.0).
     * Если score >= threshold, правило срабатывает.
     *
     * Default: 0.5
     */
    private double threshold = 0.5;

    /**
     * Версия модели для проверки совместимости.
     * Опционально: можно проверять, что используется нужная версия.
     *
     * Example: "production-15f"
     */
    private String modelVersion;

    /**
     * Действие при ошибке feature building или ML inference.
     *
     * - "PASS" (default): пропустить транзакцию при ошибке
     * - "BLOCK": заблокировать транзакцию при ошибке (более консервативно)
     *
     * Graceful degradation: при ошибках правило НЕ ломает конвейер,
     * а мягко возвращает triggered=false (PASS) или triggered=true (BLOCK).
     *
     * Default: "PASS"
     */
    private String fallbackAction = "PASS";

    public MlRuleParams() {
        this.type = RuleType.ML;
    }
}
