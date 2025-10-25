package com.example.AdminApi.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // Transaction metrics
    private final Counter transactionsProcessedCounter;
    private final Counter transactionsAlertedCounter;
    private final Counter transactionsSubmittedCounter;
    private final Counter transactionsFailedCounter;
    private final Timer transactionProcessingTimer;

    // Notification metrics (prepared for future implementation)
    private final Counter notificationsSentCounter;
    private final Counter notificationsFailedCounter;
    private final Counter notificationsRetryingCounter;
    private final Timer notificationLatencyTimer;

    // Rule metrics
    private final Counter rulesUpdateCounter;
    private final Counter rulesCreatedCounter;
    private final Counter rulesDeletedCounter;
    private final Counter validationFailedCounter;

    // Queue metrics
    private final AtomicInteger queueSizeGauge;

    // Rule evaluation metrics (per-rule tracking)
    private final ConcurrentHashMap<Long, Timer> ruleEvaluationTimers;
    private final ConcurrentHashMap<Long, Counter> ruleTriggeredCounters;
    private final ConcurrentHashMap<Long, Counter> ruleErrorCounters;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize transaction metrics
        this.transactionsProcessedCounter = Counter.builder("transactions.processed.total")
                .description("Total number of processed transactions")
                .tag("status", "processed")
                .register(meterRegistry);

        this.transactionsAlertedCounter = Counter.builder("transactions.alerted.total")
                .description("Total number of alerted transactions")
                .tag("status", "alerted")
                .register(meterRegistry);

        this.transactionsSubmittedCounter = Counter.builder("transactions.submitted.total")
                .description("Total number of transactions submitted to Kafka")
                .tag("status", "submitted")
                .register(meterRegistry);

        this.transactionsFailedCounter = Counter.builder("transactions.failed.total")
                .description("Total number of failed transactions")
                .tag("status", "failed")
                .register(meterRegistry);

        this.transactionProcessingTimer = Timer.builder("transactions.processing.duration")
                .description("Transaction processing duration")
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(meterRegistry);

        // Initialize notification metrics (will show 0 until notifications module is implemented)
        this.notificationsSentCounter = Counter.builder("notifications.sent.total")
                .description("Total number of sent notifications")
                .register(meterRegistry);

        this.notificationsFailedCounter = Counter.builder("notifications.failed.total")
                .description("Total number of failed notifications")
                .register(meterRegistry);

        this.notificationsRetryingCounter = Counter.builder("notifications.retrying.total")
                .description("Total number of retrying notifications")
                .register(meterRegistry);

        this.notificationLatencyTimer = Timer.builder("notifications.latency")
                .description("Notification delivery latency")
                .register(meterRegistry);

        // Initialize rule metrics
        this.rulesUpdateCounter = Counter.builder("rules.update.total")
                .description("Total number of rule updates")
                .register(meterRegistry);

        this.rulesCreatedCounter = Counter.builder("rules.created.total")
                .description("Total number of rules created")
                .register(meterRegistry);

        this.rulesDeletedCounter = Counter.builder("rules.deleted.total")
                .description("Total number of rules deleted")
                .register(meterRegistry);

        this.validationFailedCounter = Counter.builder("rules.validation.failed.total")
                .description("Total number of validation failures")
                .register(meterRegistry);

        // Initialize queue gauge
        this.queueSizeGauge = meterRegistry.gauge("queue.size", new AtomicInteger(0));

        // Initialize rule evaluation metrics maps
        this.ruleEvaluationTimers = new ConcurrentHashMap<>();
        this.ruleTriggeredCounters = new ConcurrentHashMap<>();
        this.ruleErrorCounters = new ConcurrentHashMap<>();
    }

    // Transaction metrics methods
    public void incrementTransactionsProcessed() {
        transactionsProcessedCounter.increment();
    }

    public void incrementTransactionsAlerted() {
        transactionsAlertedCounter.increment();
    }

    public void incrementTransactionsSubmitted() {
        transactionsSubmittedCounter.increment();
    }

    public void incrementTransactionsFailed() {
        transactionsFailedCounter.increment();
    }

    public void recordTransactionProcessingTime(Duration duration) {
        transactionProcessingTimer.record(duration);
    }

    public <T> T recordTransactionProcessing(java.util.function.Supplier<T> supplier) {
        return transactionProcessingTimer.record(supplier);
    }

    // Notification metrics methods (prepared for future use)
    public void incrementNotificationsSent() {
        notificationsSentCounter.increment();
    }

    public void incrementNotificationsFailed() {
        notificationsFailedCounter.increment();
    }

    public void incrementNotificationsRetrying() {
        notificationsRetryingCounter.increment();
    }

    public void recordNotificationLatency(Duration duration) {
        notificationLatencyTimer.record(duration);
    }

    // Rule metrics methods
    public void incrementRulesUpdate() {
        rulesUpdateCounter.increment();
    }

    public void incrementRulesCreated() {
        rulesCreatedCounter.increment();
    }

    public void incrementRulesDeleted() {
        rulesDeletedCounter.increment();
    }

    public void incrementValidationFailed() {
        validationFailedCounter.increment();
    }

    // Queue metrics methods
    public void setQueueSize(int size) {
        queueSizeGauge.set(size);
    }

    public int getQueueSize() {
        return queueSizeGauge.get();
    }

    // Getters for metrics values (useful for frontend API)
    public double getTransactionsProcessedTotal() {
        return transactionsProcessedCounter.count();
    }

    public double getTransactionsAlertedTotal() {
        return transactionsAlertedCounter.count();
    }

    public double getTransactionsSubmittedTotal() {
        return transactionsSubmittedCounter.count();
    }

    public double getTransactionsFailedTotal() {
        return transactionsFailedCounter.count();
    }

    public double getNotificationsSentTotal() {
        return notificationsSentCounter.count();
    }

    public double getNotificationsFailedTotal() {
        return notificationsFailedCounter.count();
    }

    public double getNotificationsRetryingTotal() {
        return notificationsRetryingCounter.count();
    }

    public double getRulesUpdateTotal() {
        return rulesUpdateCounter.count();
    }

    public double getValidationFailedTotal() {
        return validationFailedCounter.count();
    }

    public double getRulesCreatedTotal() {
        return rulesCreatedCounter.count();
    }

    public double getRulesDeletedTotal() {
        return rulesDeletedCounter.count();
    }

    // Rule evaluation metrics methods

    /**
     * Record rule evaluation duration and result.
     * Creates per-rule metrics on first use.
     *
     * @param ruleId Rule ID
     * @param durationMs Evaluation duration in milliseconds
     * @param triggered Whether the rule triggered
     */
    public void recordRuleEvaluation(Long ruleId, long durationMs, boolean triggered) {
        // Get or create timer for this rule
        Timer timer = ruleEvaluationTimers.computeIfAbsent(ruleId, id ->
                Timer.builder("rules.evaluation.duration")
                        .description("Rule evaluation duration")
                        .tag("rule_id", String.valueOf(id))
                        .publishPercentileHistogram()
                        .minimumExpectedValue(Duration.ofMillis(1))
                        .maximumExpectedValue(Duration.ofSeconds(1))
                        .register(meterRegistry)
        );

        timer.record(Duration.ofMillis(durationMs));
    }

    /**
     * Increment rule triggered counter.
     * Creates counter on first use.
     *
     * @param ruleId Rule ID that triggered
     */
    public void incrementRuleTriggered(Long ruleId) {
        Counter counter = ruleTriggeredCounters.computeIfAbsent(ruleId, id ->
                Counter.builder("rules.triggered.total")
                        .description("Number of times rule triggered")
                        .tag("rule_id", String.valueOf(id))
                        .register(meterRegistry)
        );

        counter.increment();
    }

    /**
     * Increment rule error counter.
     * Creates counter on first use.
     *
     * @param ruleId Rule ID that errored
     */
    public void incrementRuleErrors(Long ruleId) {
        Counter counter = ruleErrorCounters.computeIfAbsent(ruleId, id ->
                Counter.builder("rules.errors.total")
                        .description("Number of errors during rule evaluation")
                        .tag("rule_id", String.valueOf(id))
                        .register(meterRegistry)
        );

        counter.increment();
    }

    /**
     * Get rule triggered count for specific rule.
     *
     * @param ruleId Rule ID
     * @return Number of times triggered
     */
    public double getRuleTriggeredCount(Long ruleId) {
        Counter counter = ruleTriggeredCounters.get(ruleId);
        return counter != null ? counter.count() : 0.0;
    }

    /**
     * Get rule error count for specific rule.
     *
     * @param ruleId Rule ID
     * @return Number of errors
     */
    public double getRuleErrorCount(Long ruleId) {
        Counter counter = ruleErrorCounters.get(ruleId);
        return counter != null ? counter.count() : 0.0;
    }
}
