package com.example.AdminApi.services.ml;

import com.example.AdminApi.dto.EvaluationContext;
import com.example.AdminApi.models.TransactionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Сборщик признаков (features) для ML модели (production-v1).
 *
 * Переиспользует существующую инфраструктуру (TransactionHistoryStore, EvaluationContext)
 * для вычисления агрегатов и метрик из RAM (не из БД!).
 * Performance: ~5-10ms для сборки всех 15 features
 * Model version: production-v1 (15 features, 19 trees, ROC-AUC 0.5948)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureBuilder {

    private final FeatureMapper mapper;

    // Временные окна для расчета метрик
    private static final Duration DEVIATION_WINDOW = Duration.ofDays(30);
    private static final Duration VELOCITY_WINDOW = Duration.ofMinutes(10);

    /**
     * Собирает вектор из 15 features для модели (production-v1).
     *
     * Порядок features (КРИТИЧНО - должен совпадать с обучением):
     * [0] amount - сумма транзакции
     * [1] log_amount - log1p(amount)
     * [2] tx_type - тип транзакции (0-3)
     * [3] location - локация (0-7)
     * [4] channel - канал оплаты (0-3)
     * [5] hour - час транзакции (0-23)
     * [6] isNight - ночное время (22:00-6:00)
     * [7] dayOfWeek - день недели (0-6)
     * [8] time_since - секунды с последней транзакции
     * [9] deviation - отклонение от среднего
     * [10] velocity - количество транзакций за 10 минут
     * [11] cnt_10m - количество в окне (= velocity)
     * [12] sum_10m - сумма в окне 10 минут
     * [13] avg_10m - средняя сумма в окне
     * [14] isNewClient - первая транзакция клиента
     *
     * @param tx Транзакция
     * @param context Контекст с историей и агрегатами (из RAM)
     * @return Массив из 15 float значений
     */
    public float[] buildFeatures(TransactionEntity tx, EvaluationContext context) {
        float[] features = new float[15];

        try {
            long startTime = System.currentTimeMillis();

            // [0] amount - сумма транзакции
            float amount = tx.getAmount().floatValue();
            features[0] = amount;

            // [1] log_amount - log1p(amount)
            features[1] = (float) Math.log1p(amount);

            // [2] tx_type - тип транзакции (0-3)
            features[2] = mapper.mapTransactionType(tx.getDescription());

            // [3] location - локация (0-7)
            features[3] = mapper.mapLocation(tx.getGeo());

            // [4] channel - канал оплаты (0-3)
            features[4] = mapper.mapPaymentChannel(tx.getChannel());

            // [5] hour - час транзакции (0-23)
            features[5] = tx.getTimestamp().getHour();

            // [6] isNight - ночное время (22:00-6:00)
            int hour = tx.getTimestamp().getHour();
            features[6] = (hour >= 22 || hour <= 6) ? 1.0f : 0.0f;

            // [7] dayOfWeek - день недели (0-6, Monday=0)
            features[7] = tx.getTimestamp().getDayOfWeek().getValue() - 1;

            // [8] time_since - секунды с последней транзакции
            features[8] = calculateTimeSinceLast(tx, context);

            // [9] deviation - отклонение от среднего
            features[9] = calculateSpendingDeviation(tx, context);

            // [10] velocity - количество транзакций за 10 минут
            int velocity = context.getHistoryStore()
                .countInWindow(tx.getSourceId(), VELOCITY_WINDOW);
            features[10] = velocity;

            // [11] cnt_10m - количество в окне (= velocity)
            features[11] = velocity;

            // [12] sum_10m - сумма в окне 10 минут
            BigDecimal sum10m = context.getHistoryStore()
                .sumInWindow(tx.getSourceId(), VELOCITY_WINDOW);
            features[12] = sum10m.floatValue();

            // [13] avg_10m - средняя сумма в окне
            float avg10m = velocity > 0 ? features[12] / velocity : 0.0f;
            features[13] = avg10m;

            // [14] isNewClient - первая транзакция клиента
            features[14] = (context.getHistoryStore()
                .getLastTransactionTime(tx.getSourceId()) == null) ? 1.0f : 0.0f;

            long buildTime = System.currentTimeMillis() - startTime;
            log.debug("Features built for tx {} in {}ms: amount={}, log_amount={:.2f}, type={}, location={}, velocity={}, deviation={:.2f}, isNewClient={}",
                tx.getCorrelationId(), buildTime,
                features[0], features[1], (int)features[2], (int)features[3], (int)features[10], features[9], (int)features[14]);

        } catch (Exception e) {
            log.error("Error building features for tx {}: {}", tx.getCorrelationId(), e.getMessage());
            // Возвращаем безопасные дефолты
            return getDefaultFeatures(tx);
        }

        return features;
    }

    /**
     * Вычисляет время с последней транзакции (в секундах).
     * Читает из RAM (TransactionHistoryStore), не из БД!
     *
     * Fallback: 300 секунд (5 минут) если нет истории.
     */
    private float calculateTimeSinceLast(TransactionEntity tx, EvaluationContext context) {
        LocalDateTime lastTxTime = context.getHistoryStore()
            .getLastTransactionTime(tx.getSourceId());

        if (lastTxTime == null) {
            log.debug("No previous transactions for {}, using default 300s", tx.getSourceId());
            return 300.0f; // дефолт: 5 минут
        }

        long seconds = Duration.between(lastTxTime, tx.getTimestamp()).getSeconds();
        return Math.max(seconds, 1.0f); // минимум 1 секунда
    }

    /**
     * Вычисляет отклонение от среднего поведения.
     * spending_deviation_score = (amount - avg) / std
     *
     * Читает из RAM (TransactionHistoryStore), не из БД!
     *
     * Fallback: 0.0 если нет истории или std = 0
     */
    private float calculateSpendingDeviation(TransactionEntity tx, EvaluationContext context) {
        BigDecimal avg = context.getHistoryStore()
            .getAvgAmount(tx.getSourceId(), DEVIATION_WINDOW);

        double std = context.getHistoryStore()
            .getStdAmount(tx.getSourceId(), DEVIATION_WINDOW);

        if (std == 0 || avg.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("No deviation data for {}, using 0.0", tx.getSourceId());
            return 0.0f; // нейтральное значение
        }

        double deviation = (tx.getAmount().doubleValue() - avg.doubleValue()) / std;
        return (float) deviation;
    }

    /**
     * Вычисляет amount_on_time = amount / time_since_last.
     * Fallback: 0.5 если time_since_last = 0
     */
    private float calculateAmountOnTime(float amount, float timeSince) {
        if (timeSince <= 0) {
            return 0.5f; // дефолт по условию задачи
        }
        return amount / timeSince;
    }

    /**
     * Возвращает безопасные дефолтные features при ошибке.
     * Позволяет не ломать конвейер при проблемах сборки признаков.
     */
    private float[] getDefaultFeatures(TransactionEntity tx) {
        log.warn("Using default features for tx {}", tx.getCorrelationId());
        float amount = tx.getAmount().floatValue();
        return new float[]{
            amount,  // [0] amount
            (float) Math.log1p(amount),  // [1] log_amount
            3,  // [2] tx_type: withdrawal
            6,  // [3] location: Tokyo
            2,  // [4] channel: card
            12,  // [5] hour: noon
            0,  // [6] isNight: false
            0,  // [7] dayOfWeek: Monday
            300.0f,  // [8] time_since: 5 min
            0.0f,  // [9] deviation: neutral
            0,  // [10] velocity: 0
            0,  // [11] cnt_10m: 0
            0,  // [12] sum_10m: 0
            0,  // [13] avg_10m: 0
            0   // [14] isNewClient: false
        };
    }
}
