package com.example.AdminApi.services;

import com.example.AdminApi.dto.TransactionRecord;
import com.example.AdminApi.models.TransactionEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * In-memory хранилище истории транзакций для Pattern правил.
 *
 * Особенности:
 * - Thread-safe (ConcurrentHashMap + ConcurrentLinkedDeque)
 * - Sliding window для временных окон
 * - Автоматическая очистка старых записей (eviction)
 * - Лимиты на количество записей и время хранения
 *
 * Используется для Pattern правил типа:
 * - "N транзакций за последние X минут"
 * - "Сумма транзакций за окно времени"
 * - "rapid_small_transfers" паттерн
 *
 * Performance considerations:
 * - O(1) для add() - добавление в начало deque
 * - O(n) для getInWindow() - фильтрация по времени, где n = размер deque для ключа
 * - Eviction ограничивает n до MAX_RECORDS_PER_KEY
 *
 * Memory considerations:
 * - Каждая запись ~100 bytes (timestamp, amount, 2 strings)
 * - Макс. 100 записей на ключ * 1000 ключей = 10MB
 * - Очистка каждую минуту предотвращает утечки памяти
 */

@Slf4j
@Service
public class TransactionHistoryStore {

    /**
     * Хранилище: sourceId -> deque последних транзакций.
     *
     * ConcurrentHashMap обеспечивает thread-safety для put/get операций.
     * ConcurrentLinkedDeque обеспечивает thread-safety для add/remove операций.
     */
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<TransactionRecord>> history =
        new ConcurrentHashMap<>();

    /**
     * Максимум записей на один ключ (sourceId).
     *
     * Ограничивает использование памяти и ускоряет getInWindow().
     * При превышении самые старые записи удаляются (FIFO).
     */
    private static final int MAX_RECORDS_PER_KEY = 100;

    /**
     * Максимальное время хранения записи.
     *
     * Записи старше этого времени удаляются при cleanup.
     * 24 часа достаточно для большинства Pattern правил.
     */
    private static final Duration MAX_RETENTION = Duration.ofHours(24);

    /**
     * Добавить транзакцию в историю.
     *
     * Транзакция добавляется в начало deque (самая свежая).
     * Применяется eviction если превышен лимит записей.
     *
     * @param transaction Транзакция для добавления
     */
    public void add(TransactionEntity transaction) {
        if (transaction == null || transaction.getSourceId() == null) {
            log.warn("Cannot add null transaction or transaction with null sourceId");
            return;
        }

        String sourceId = transaction.getSourceId();

        log.info("HISTORY STORE DEBUG: Adding transaction sourceId={}, correlationId={}, timestamp={}",
                sourceId, transaction.getCorrelationId(), transaction.getTimestamp());

        // Создаем облегченную запись (только нужные поля)
        TransactionRecord record = TransactionRecord.builder()
                .timestamp(transaction.getTimestamp())
                .amount(transaction.getAmount())
                .sourceId(transaction.getSourceId())
                .destinationId(transaction.getDestinationId())
                .currency(transaction.getCurrency())
                .correlationId(transaction.getCorrelationId().toString())
                .build();

        // Добавляем в начало deque (O(1) операция)
        ConcurrentLinkedDeque<TransactionRecord> records =
            history.computeIfAbsent(sourceId, k -> new ConcurrentLinkedDeque<>());

        records.addFirst(record);

        // Eviction: удаляем старые записи если превысили лимит
        while (records.size() > MAX_RECORDS_PER_KEY) {
            records.removeLast(); // Удаляем самую старую запись
        }

        log.info("HISTORY STORE DEBUG: Added transaction to history: sourceId={}, historySize={}, correlationId={}",
                  sourceId, records.size(), transaction.getCorrelationId());
    }

    /**
     * Получить транзакции в скользящем окне (sliding window).
     *
     * Возвращает все транзакции для ключа, которые попадают в окно времени.
     * Окно "скользит" - всегда относительно текущего момента времени.
     *
     * Пример:
     * - Сейчас 10:00
     * - window = Duration.ofMinutes(10)
     * - Вернутся транзакции с timestamp >= 09:50
     *
     * @param sourceId Ключ (обычно sourceId транзакции)
     * @param window Размер окна (например, Duration.ofMinutes(10))
     * @return Список транзакций в окне (отсортирован от новых к старым)
     */
    public List<TransactionRecord> getInWindow(String sourceId, Duration window) {
        return getInWindow(sourceId, window, LocalDateTime.now());
    }

    /**
     * Получить транзакции в скользящем окне относительно заданного времени.
     *
     * Возвращает все транзакции для ключа, которые попадают в окно времени
     * относительно referenceTime.
     *
     * Пример:
     * - referenceTime = 10:00
     * - window = Duration.ofMinutes(10)
     * - Вернутся транзакции с timestamp >= 09:50 (включая текущую, если она в 10:00)
     *
     * @param sourceId Ключ (обычно sourceId транзакции)
     * @param window Размер окна (например, Duration.ofMinutes(10))
     * @param referenceTime Референсное время (обычно timestamp текущей транзакции)
     * @return Список транзакций в окне (отсортирован от новых к старым)
     */
    public List<TransactionRecord> getInWindow(String sourceId, Duration window, LocalDateTime referenceTime) {
        if (sourceId == null || window == null || referenceTime == null) {
            log.warn("Cannot get window with null sourceId, window or referenceTime");
            return List.of();
        }

        LocalDateTime cutoff = referenceTime.minus(window);

        log.info("HISTORY STORE DEBUG: getInWindow for sourceId={}, window={}, referenceTime={}, cutoff={}",
                sourceId, window, referenceTime, cutoff);

        List<TransactionRecord> result = history.getOrDefault(sourceId, new ConcurrentLinkedDeque<>())
                     .stream()
                     .filter(r -> !r.getTimestamp().isBefore(cutoff) && !r.getTimestamp().isAfter(referenceTime))
                     .collect(Collectors.toList());

        log.info("HISTORY STORE DEBUG: Found {} transactions in window", result.size());

        return result;
    }

    /**
     * Получить количество транзакций в окне.
     *
     * Удобный метод для правил типа "N транзакций за X минут".
     *
     * @param sourceId Ключ
     * @param window Размер окна
     * @return Количество транзакций в окне
     */
    public int countInWindow(String sourceId, Duration window) {
        return getInWindow(sourceId, window).size();
    }

    /**
     * Получить сумму транзакций в окне.
     *
     * Удобный метод для правил типа "сумма транзакций > X за Y минут".
     *
     * @param sourceId Ключ
     * @param window Размер окна
     * @return Сумма транзакций в окне
     */
    public BigDecimal sumInWindow(String sourceId, Duration window) {
        return getInWindow(sourceId, window)
                .stream()
                .map(TransactionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Получить максимальную сумму транзакции в окне.
     *
     * @param sourceId Ключ
     * @param window Размер окна
     * @return Максимальная сумма или BigDecimal.ZERO если окно пустое
     */
    public BigDecimal maxInWindow(String sourceId, Duration window) {
        return getInWindow(sourceId, window)
                .stream()
                .map(TransactionRecord::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Получить минимальную сумму транзакции в окне.
     *
     * @param sourceId Ключ
     * @param window Размер окна
     * @return Минимальная сумма или BigDecimal.ZERO если окно пустое
     */
    public BigDecimal minInWindow(String sourceId, Duration window) {
        return getInWindow(sourceId, window)
                .stream()
                .map(TransactionRecord::getAmount)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Автоматическая очистка старых записей (eviction).
     *
     * Выполняется каждую минуту через Spring @Scheduled.
     * Удаляет записи старше MAX_RETENTION (24 часа).
     * Удаляет ключи с пустыми deque.
     *
     * Это предотвращает:
     * - Утечки памяти
     * - Замедление getInWindow() из-за большого количества записей
     */
    @Scheduled(fixedRate = 60000) // 60 секунд = 1 минута
    public void cleanupOld() {
        LocalDateTime cutoff = LocalDateTime.now().minus(MAX_RETENTION);
        int removedKeys = 0;
        int removedRecords = 0;

        // Проходим по всем ключам
        for (var entry : history.entrySet()) {
            String key = entry.getKey();
            ConcurrentLinkedDeque<TransactionRecord> records = entry.getValue();

            int sizeBefore = records.size();

            // Удаляем старые записи
            records.removeIf(r -> r.getTimestamp().isBefore(cutoff));

            int sizeAfter = records.size();
            removedRecords += (sizeBefore - sizeAfter);

            // Удаляем ключ если deque пустой
            if (records.isEmpty()) {
                history.remove(key);
                removedKeys++;
            }
        }

        if (removedKeys > 0 || removedRecords > 0) {
            log.info("TransactionHistoryStore cleanup: removed {} keys, {} records. Current: {} keys, {} total records",
                     removedKeys, removedRecords, getTotalKeys(), getTotalRecords());
        }
    }

    /**
     * Получить общее количество ключей в хранилище.
     *
     * @return Количество уникальных sourceId в истории
     */
    public int getTotalKeys() {
        return history.size();
    }

    /**
     * Получить общее количество записей во всех ключах.
     *
     * @return Общее количество транзакций в истории
     */
    public int getTotalRecords() {
        return history.values().stream()
                      .mapToInt(ConcurrentLinkedDeque::size)
                      .sum();
    }

    /**
     * Получить размер истории для конкретного ключа.
     *
     * @param sourceId Ключ
     * @return Количество записей для этого ключа
     */
    public int getRecordsCount(String sourceId) {
        ConcurrentLinkedDeque<TransactionRecord> records = history.get(sourceId);
        return records != null ? records.size() : 0;
    }

    /**
     * Очистить всю историю (для тестов).
     *
     * ВНИМАНИЕ: Использовать только в тестах!
     */
    public void clear() {
        history.clear();
        log.warn("TransactionHistoryStore cleared (should only be used in tests!)");
    }

    /**
     * Получить статистику хранилища (для мониторинга).
     *
     * @return Строка со статистикой
     */
    public String getStats() {
        int totalKeys = getTotalKeys();
        int totalRecords = getTotalRecords();
        double avgRecordsPerKey = totalKeys > 0 ? (double) totalRecords / totalKeys : 0;

        return String.format(
            "TransactionHistoryStore: %d keys, %d records (avg %.1f per key)",
            totalKeys, totalRecords, avgRecordsPerKey
        );
    }

    /**
     * Логировать текущую статистику.
     * Полезно для отладки и мониторинга.
     */
    public void logStats() {
        log.info(getStats());
    }

    /**
     * Получить timestamp последней транзакции (для time_since_last).
     *
     * Используется для расчета time_since_last_transaction в ML features.
     *
     * @param sourceId Ключ (sourceId)
     * @return Timestamp последней транзакции или null если нет истории
     */
    public LocalDateTime getLastTransactionTime(String sourceId) {
        List<TransactionRecord> records = getInWindow(sourceId, Duration.ofHours(24));
        if (records.isEmpty()) {
            return null;
        }
        // Первая запись - самая свежая (addFirst в deque)
        return records.get(0).getTimestamp();
    }

    /**
     * Получить среднюю сумму транзакций (для spending_deviation).
     *
     * Используется для расчета spending_deviation_score в ML features.
     *
     * @param sourceId Ключ (sourceId)
     * @param window Размер окна (например, Duration.ofDays(30))
     * @return Средняя сумма или BigDecimal.ZERO если нет транзакций
     */
    public BigDecimal getAvgAmount(String sourceId, Duration window) {
        List<TransactionRecord> records = getInWindow(sourceId, window);
        if (records.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = records.stream()
                .map(TransactionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(records.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Получить стандартное отклонение суммы транзакций (для spending_deviation_score).
     *
     * Вычисляет стандартное отклонение (standard deviation) для обнаружения
     * аномальных транзакций.
     *
     * @param sourceId Ключ (sourceId)
     * @param window Размер окна (например, Duration.ofDays(30))
     * @return Стандартное отклонение или 0.0 если недостаточно данных
     */
    public double getStdAmount(String sourceId, Duration window) {
        List<TransactionRecord> records = getInWindow(sourceId, window);
        if (records.size() < 2) {
            return 0.0; // Нужно минимум 2 записи для расчета std
        }

        double avg = getAvgAmount(sourceId, window).doubleValue();

        double variance = records.stream()
                .mapToDouble(r -> {
                    double diff = r.getAmount().doubleValue() - avg;
                    return diff * diff;
                })
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }
}
