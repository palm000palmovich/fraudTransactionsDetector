package com.example.AdminApi.services;

import com.example.AdminApi.dto.InputTransactionDto;
import com.example.AdminApi.dto.RuleEngineResult;
import com.example.AdminApi.models.TransactionEntity;
import com.example.AdminApi.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Process an incoming transaction from Kafka.
     *
     * @param dto The transaction data from Kafka message
     */
    @Transactional
    public void processTransaction(InputTransactionDto dto) {
        log.info("Processing transaction: correlationId={}", dto.getCorrelationId());

        // Step 1: Convert DTO to Entity
        TransactionEntity transaction = convertToEntity(dto);
        transaction.setStatus("PENDING");

        // Step 2: Save to database with status PENDING
        transaction = transactionRepository.save(transaction);
        log.info("Transaction saved to DB: id={}, correlationId={}, status={}",
                 transaction.getId(), dto.getCorrelationId(), transaction.getStatus());

        // Step 3: Apply fraud detection rules (FIRST-MATCH policy)
        RuleEngineResult ruleResult = ruleEngineService.evaluate(transaction);

        // Step 4: Update transaction status based on rule evaluation
        if (ruleResult.isTriggered()) {
            // Rule triggered - mark as ALERTED
            transaction.setStatus("ALERTED");
            transaction.setTriggeredRuleId(ruleResult.getRuleId());
            transaction.setTriggeredRuleName(ruleResult.getRuleName());
            transaction.setTriggerReason(ruleResult.getReason());

            log.warn("Transaction ALERTED: correlationId={}, rule='{}', reason={}",
                     dto.getCorrelationId(), ruleResult.getRuleName(), ruleResult.getReason());
        } else {
            // No rules triggered - mark as PROCESSED
            transaction.setStatus("PROCESSED");
        }

        transaction.setProcessedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        log.info("Transaction processed: correlationId={}, finalStatus={}",
                 dto.getCorrelationId(), transaction.getStatus());

        // Step 5: Send notification if triggered
        // TODO: Implement NotificationService integration
        if (ruleResult.isTriggered()) {
            log.info("TODO: Send notification for alerted transaction: correlationId={}",
                     dto.getCorrelationId());
            // notificationService.sendAlert(transaction, ruleResult);
        }
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
