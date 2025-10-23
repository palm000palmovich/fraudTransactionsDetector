package com.example.AdminApi.consumer;

import com.example.AdminApi.dto.InputTransactionDto;
import com.example.AdminApi.services.TransactionProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer for processing transactions from input-transactions topic.
 *
 * This consumer:
 * - Listens to the input-transactions topic
 * - Extracts correlation ID for tracing
 * - Delegates processing to TransactionProcessingService
 * - Handles errors gracefully
 * - Uses manual acknowledgment for reliability
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionConsumer {

    private final TransactionProcessingService processingService;

    /**
     * Consumes transactions from Kafka topic and processes them.
     *
     * Configuration:
     * - Topic: ${app.kafka.topic.input-transactions}
     * - Group ID: transaction-processor
     * - Concurrency: 3 (processes 3 messages in parallel)
     * - Acknowledgment: Manual (commit after successful processing)
     *
     * @param dto The transaction to process
     * @param partition The Kafka partition number
     * @param offset The message offset in the partition
     * @param ack Manual acknowledgment handle
     */
    @KafkaListener(
        topics = "${app.kafka.topic.input-transactions}",
        groupId = "transaction-processor",
        concurrency = "3"
    )
    public void consume(
            @Payload InputTransactionDto dto,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        String correlationId = dto.getCorrelationId() != null
            ? dto.getCorrelationId().toString()
            : "unknown";

        // Add correlation ID to MDC for structured logging
        MDC.put("correlationId", correlationId);

        try {
            log.info("Consumed transaction from Kafka: correlationId={}, partition={}, offset={}",
                     correlationId, partition, offset);

            // Process the transaction (save to DB, apply rules, send notifications)
            processingService.processTransaction(dto);

            // Acknowledge successful processing (commit offset)
            if (ack != null) {
                ack.acknowledge();
            }

            log.info("Transaction processed successfully: correlationId={}", correlationId);

        } catch (Exception e) {
            log.error("Failed to process transaction: correlationId={}, partition={}, offset={}, error={}",
                      correlationId, partition, offset, e.getMessage(), e);

            // TODO: Implement retry mechanism or send to DLQ
            // For now, we acknowledge to prevent infinite reprocessing
            if (ack != null) {
                ack.acknowledge();
            }

        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.remove("correlationId");
        }
    }
}
