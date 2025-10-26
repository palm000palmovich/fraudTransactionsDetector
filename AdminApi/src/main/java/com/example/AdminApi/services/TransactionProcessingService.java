package com.example.AdminApi.services;

import com.example.AdminApi.dto.InputTransactionDto;
import com.example.AdminApi.dto.MessageAlertDto;
import com.example.AdminApi.dto.RuleEngineResult;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service for processing incoming transactions.
 *
 * Workflow:
 * 1. Convert InputTransactionDto to TransactionEntity
 * 2. Save to database with status PENDING
 * 3. Apply fraud detection rules (RuleEngineService)
 * 4. Update status to PROCESSED or ALERTED
 * 5. Send notifications if triggered (NotificationService)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionProcessingService {
    private final TransactionRepository transactionRepository;
    private final RuleEngineService ruleEngineService;
    private final MetricsService metricsService;
    private final TransactionLogService transactionLogService;
    private final TransactionHistoryStore historyStore;
    private final KafkaTemplate<String, MessageAlertDto> alertKafkaTemplate;
    @Value("${app.kafka.topic.alerted-transactions}")
    private String alertTransactionsTopic;

    /**
     * Process an incoming transaction from Kafka.
     *
     * @param dto The transaction data from Kafka message
     */
    @Transactional
    public void processTransaction(InputTransactionDto dto) {
        TransactionEntity transaction = null;

        try {
            // Wrap entire processing in a timer to measure latency
            metricsService.recordTransactionProcessing(() -> {
                log.info("Processing transaction: correlationId={}", dto.getCorrelationId());

                // Log: Kafka consumer received transaction
                transactionLogService.log(
                        dto.getCorrelationId(),
                        "INFO",
                        "KAFKA_CONSUMER",
                        "Transaction received from Kafka topic"
                );

                // Step 1: Convert DTO to Entity
                TransactionEntity txn = convertToEntity(dto);
                txn.setStatus("PENDING");

                // Step 2: Save to database with status PENDING
                TransactionEntity savedTxn = transactionRepository.save(txn);
                log.info("Transaction saved to DB: id={}, correlationId={}, status={}",
                         savedTxn.getId(), dto.getCorrelationId(), savedTxn.getStatus());

                // Log: Transaction saved to database
                transactionLogService.log(
                        dto.getCorrelationId(),
                        "INFO",
                        "KAFKA_CONSUMER",
                        "Transaction saved to database",
                        String.format("{\"id\":%d,\"status\":\"%s\"}", savedTxn.getId(), savedTxn.getStatus())
                );

                // Step 2.5: Add transaction to history store (for Pattern rules)
                historyStore.add(savedTxn);
                log.debug("Transaction added to history store: sourceId={}, historySize={}",
                          savedTxn.getSourceId(), historyStore.getRecordsCount(savedTxn.getSourceId()));

                // Step 3: Apply fraud detection rules (FIRST-MATCH policy)
                transactionLogService.log(
                        dto.getCorrelationId(),
                        "INFO",
                        "RULES",
                        "Starting rule evaluation"
                );

                RuleEngineResult ruleResult = ruleEngineService.evaluate(savedTxn);

                // Step 4: Update transaction status based on rule evaluation
                if (ruleResult.isTriggered()) {
                    // Rule triggered - mark as ALERTED
                    savedTxn.setStatus("ALERTED");
                    savedTxn.setTriggeredRuleId(ruleResult.getRuleId());
                    savedTxn.setTriggeredRuleName(ruleResult.getRuleName());
                    savedTxn.setTriggerReason(ruleResult.getReason());
                    savedTxn.setRuleMetadata(ruleResult.getMetadata());

                    log.warn("Transaction ALERTED: correlationId={}, rule='{}', reason={}",
                             dto.getCorrelationId(), ruleResult.getRuleName(), ruleResult.getReason());

                    // Log: Rule triggered
                    transactionLogService.log(
                            dto.getCorrelationId(),
                            "WARN",
                            "RULES",
                            String.format("Rule triggered: %s", ruleResult.getRuleName()),
                            String.format("{\"ruleId\":%d,\"ruleName\":\"%s\",\"reason\":\"%s\"}",
                                    ruleResult.getRuleId(),
                                    ruleResult.getRuleName(),
                                    ruleResult.getReason())
                    );

                    // Track alerted transaction metric
                    metricsService.incrementTransactionsAlerted();

                    transactionLogService.log(
                            dto.getCorrelationId(),
                            "INFO",
                            "NOTIFICATION",
                            "Notification sending, correlation id: " + savedTxn.getCorrelationId()
                    );

                    MessageAlertDto messageAlertDto = new MessageAlertDto(savedTxn.getTriggeredRuleId(),
                            savedTxn.getTriggeredRuleName(),
                            savedTxn.getTriggerReason(),
                            LocalDateTime.now(),
                            savedTxn.getCorrelationId());

                    sendAlertToTopic(messageAlertDto);
                } else {
                    // No rules triggered - mark as PROCESSED
                    savedTxn.setStatus("PROCESSED");

                    // Log: No rules triggered
                    transactionLogService.log(
                            dto.getCorrelationId(),
                            "INFO",
                            "RULES",
                            "No rules triggered, transaction approved"
                    );

                    // Track processed transaction metric
                    metricsService.incrementTransactionsProcessed();
                }

                savedTxn.setProcessedAt(LocalDateTime.now());
                transactionRepository.save(savedTxn);

                log.info("Transaction processed: correlationId={}, finalStatus={}",
                         dto.getCorrelationId(), savedTxn.getStatus());

                // Log: Transaction processing completed
                transactionLogService.log(
                        dto.getCorrelationId(),
                        "INFO",
                        "KAFKA_CONSUMER",
                        String.format("Transaction processing completed with status: %s", savedTxn.getStatus()),
                        String.format("{\"finalStatus\":\"%s\",\"processedAt\":\"%s\"}",
                                savedTxn.getStatus(),
                                savedTxn.getProcessedAt())
                );

                return null; // Timer requires a return value
            });
        } catch (Exception e) {
            // Track failed transaction metric
            metricsService.incrementTransactionsFailed();

            log.error("Failed to process transaction: correlationId={}, error={}",
                     dto.getCorrelationId(), e.getMessage(), e);

            // Log: Processing error
            transactionLogService.log(
                    dto.getCorrelationId(),
                    "ERROR",
                    "KAFKA_CONSUMER",
                    "Transaction processing failed",
                    String.format("{\"error\":\"%s\",\"errorType\":\"%s\"}",
                            e.getMessage(),
                            e.getClass().getSimpleName())
            );

            // Try to save transaction with FAILED status
            try {
                if (transaction == null) {
                    transaction = convertToEntity(dto);
                }
                transaction.setStatus("FAILED");
                transaction.setTriggerReason("Processing error: " + e.getMessage());
                transaction.setProcessedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
            } catch (Exception saveError) {
                log.error("Failed to save FAILED transaction status: correlationId={}, error={}",
                         dto.getCorrelationId(), saveError.getMessage());
            }

            // Re-throw to let Kafka consumer know about the failure
            throw new RuntimeException("Transaction processing failed for correlationId=" +
                                     dto.getCorrelationId(), e);
        }
    }

    //Sending to alert topic
    private void sendAlertToTopic(MessageAlertDto messageAlertDto) {
        log.info("Attempt to send alert-message: {}", messageAlertDto.toString());

        alertKafkaTemplate.send(
                alertTransactionsTopic, messageAlertDto.getTransactionCorrelationId().toString(),
                        messageAlertDto)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Sent alert message to topic {}, correlation id: {}", alertTransactionsTopic,
                                messageAlertDto.getTransactionCorrelationId());
                    } else {
                        log.error("Failed to send topic {}, correlation id: {}", alertTransactionsTopic,
                                messageAlertDto.getTransactionCorrelationId());
                    }
                });
    }

    /**
     * Convert InputTransactionDto to TransactionEntity.
     *
     * @param dto The input DTO from Kafka
     * @return TransactionEntity ready to be saved
     */
    private TransactionEntity convertToEntity(InputTransactionDto dto) {
        TransactionEntity entity = new TransactionEntity();

        // Set correlation ID for tracing
        entity.setCorrelationId(dto.getCorrelationId());

        // Set transaction data
        entity.setSourceId(dto.getSourceId());
        entity.setDestinationId(dto.getDestinationId());
        entity.setAmount(BigDecimal.valueOf(dto.getAmount()));
        entity.setCurrency(dto.getCurrency());
        entity.setTimestamp(dto.getTimeStamp());
        entity.setChannel(dto.getChannel());
        entity.setGeo(dto.getGeo());
        entity.setDescription(dto.getDescription());

        return entity;
    }
}
