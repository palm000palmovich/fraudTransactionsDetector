package com.example.AdminApi.dto;

import com.example.AdminApi.services.TransactionHistoryStore;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Контекст выполнения правил (Evaluation Context).
 *
 * Содержит всю вспомогательную информацию для вычисления правил:
 * - История транзакций (для Pattern правил)
 * - Флаги клиентов (для условий типа "новый клиент", "рисковый клиент")
 * - Счетчики (для правил "N транзакций за день/час")
 * - Кэш результатов (для Composite правил, чтобы избежать повторных вычислений)
 * - Временная метка оценки (для консистентности временных проверок)
 *
 * Контекст создается один раз для каждой транзакции и передается во все evaluators.
 * Это позволяет:
 * 1. Избежать повторных запросов в БД/кэш
 * 2. Кэшировать промежуточные результаты для Composite правил
 * 3. Обеспечить консистентность временных проверок
 *
 * Thread-safety: EvaluationContext является immutable после создания,
 * но внутренние Map-ы mutable для кэширования результатов.
 */
@Data
@Builder
public class EvaluationContext {

    /**
     * Хранилище истории транзакций для Pattern правил.
     *
     * Используется для sliding window вычислений:
     * - "N транзакций за последние X минут"
     * - "Сумма транзакций за окно времени"
     * - "Максимальная/минимальная сумма в окне"
     *
     * Пример использования:
     * List<TransactionRecord> recent = context.getHistoryStore()
     *     .getInWindow("ACC001", Duration.ofMinutes(10));
     */
    private TransactionHistoryStore historyStore;

    /**
     * Флаги клиента (map: ключ -> значение).
     *
     * Используется для условий в правилах:
     * - "isNew_ACC001" -> true (новый клиент, зарегистрирован < 30 дней)
     * - "riskLevel_ACC001" -> "high" (высокий уровень риска)
     * - "verified_ACC001" -> false (не верифицирован)
     * - "country_ACC001" -> "US" (страна клиента)
     *
     * Флаги загружаются из БД/кэша при создании контекста.
     *
     * Пример правила:
     * "amount > 50000 для новых клиентов"
     * -> проверяем clientFlags.get("isNew_" + sourceId)
     */
    @Builder.Default
    private Map<String, Object> clientFlags = new HashMap<>();

    /**
     * Счетчики транзакций (map: ключ -> количество).
     *
     * Используется для правил с временными агрегатами:
     * - "daily_ACC001" -> 15 (транзакций за сегодня)
     * - "hourly_ACC001" -> 3 (транзакций за последний час)
     * - "weekly_ACC001" -> 87 (транзакций за неделю)
     *
     * Счетчики вычисляются при создании контекста на основе historyStore
     * или загружаются из БД/кэша.
     *
     * Пример правила:
     * "больше 10 транзакций за день"
     * -> проверяем counters.get("daily_" + sourceId) > 10
     */
    @Builder.Default
    private Map<String, Integer> counters = new HashMap<>();

    /**
     * Кэш результатов вычислений (для Composite правил).
     *
     * Используется для избежания повторных вычислений одного и того же условия.
     *
     * Ключ: уникальный идентификатор условия (например, "amount>50000")
     * Значение: результат вычисления (true/false)
     *
     * Пример:
     * Composite правило: (amount > 50000) AND (amount > 50000) AND (hour >= 22)
     * - Первая проверка "amount > 50000" -> вычисляем и кэшируем
     * - Вторая проверка "amount > 50000" -> берем из кэша (не вычисляем!)
     * - Третья проверка "hour >= 22" -> вычисляем
     *
     * Кэш очищается после завершения вычисления правил для транзакции.
     */
    @Builder.Default
    private Map<String, Boolean> evaluationCache = new HashMap<>();

    /**
     * Временная метка текущей оценки.
     *
     * Используется для консистентности временных проверок:
     * - Все правила используют одно и то же "сейчас"
     * - Избегаем ситуации, когда одна проверка видит 22:59, а другая 23:00
     *
     * Устанавливается при создании контекста и не меняется во время вычисления.
     */
    @Builder.Default
    private LocalDateTime evaluationTime = LocalDateTime.now();

    /**
     * Дополнительные метаданные (расширяемое поле).
     *
     * Может использоваться для:
     * - Передачи дополнительных данных между evaluators
     * - Логирования и отладки
     * - Будущих расширений без изменения интерфейса
     *
     * Пример:
     * metadata.put("ruleEngineVersion", "1.0.0");
     * metadata.put("debug", true);
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // --- Вспомогательные методы ---

    /**
     * Получить флаг клиента по ключу с fallback значением.
     *
     * @param key Ключ флага (например, "isNew_ACC001")
     * @param defaultValue Значение по умолчанию если флаг не найден
     * @return Значение флага или defaultValue
     */
    public Object getClientFlag(String key, Object defaultValue) {
        return clientFlags.getOrDefault(key, defaultValue);
    }

    /**
     * Получить счетчик по ключу с fallback значением.
     *
     * @param key Ключ счетчика (например, "daily_ACC001")
     * @param defaultValue Значение по умолчанию если счетчик не найден
     * @return Значение счетчика или defaultValue
     */
    public int getCounter(String key, int defaultValue) {
        return counters.getOrDefault(key, defaultValue);
    }

    /**
     * Проверить наличие результата в кэше.
     *
     * @param key Ключ условия
     * @return true если результат закэширован
     */
    public boolean hasCachedResult(String key) {
        return evaluationCache.containsKey(key);
    }

    /**
     * Получить закэшированный результат.
     *
     * @param key Ключ условия
     * @return Закэшированный результат или null
     */
    public Boolean getCachedResult(String key) {
        return evaluationCache.get(key);
    }

    /**
     * Закэшировать результат вычисления.
     *
     * @param key Ключ условия
     * @param result Результат вычисления
     */
    public void cacheResult(String key, boolean result) {
        evaluationCache.put(key, result);
    }

    /**
     * Получить текущий час для временных проверок.
     * Использует evaluationTime для консистентности.
     *
     * @return Час (0-23)
     */
    public int getCurrentHour() {
        return evaluationTime.getHour();
    }

    /**
     * Получить день недели для временных проверок.
     * Использует evaluationTime для консистентности.
     *
     * @return День недели (1=Monday, 7=Sunday)
     */
    public int getDayOfWeek() {
        return evaluationTime.getDayOfWeek().getValue();
    }

    /**
     * Проверить является ли текущее время "ночью" (22:00 - 06:00).
     *
     * @return true если ночь
     */
    public boolean isNightTime() {
        int hour = getCurrentHour();
        return hour >= 22 || hour < 6;
    }

    /**
     * Проверить является ли текущий день выходным (суббота или воскресенье).
     *
     * @return true если выходной
     */
    public boolean isWeekend() {
        int day = getDayOfWeek();
        return day == 6 || day == 7; // Saturday=6, Sunday=7
    }
}
