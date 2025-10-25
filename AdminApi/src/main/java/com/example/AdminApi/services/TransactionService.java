package com.example.AdminApi.services;

import com.example.AdminApi.dto.InputTransactionDto;
import com.example.AdminApi.dto.MakeTransactionDto;
import com.example.AdminApi.exceptions.RequestLimitException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final KafkaTemplate<String, InputTransactionDto> inputKafkaTemplate;
    private final KafkaTemplate<String, Object> dlqKafkaTemplate;
    private final MetricsService metricsService;
    private final TransactionLogService transactionLogService;

    @Value("${app.kafka.topic.input-transactions}")
    private String inputTopic;
    @Value("${app.kafka.topic.input-transactions-dlq}")
    private String inputTransactionsDlq;
    @Value("${app.transaction.max-concurrent}")
    private int maxConcurrent;

    private Semaphore transactionSemaphore;

    @PostConstruct
    public void init() {
        this.transactionSemaphore = new Semaphore(maxConcurrent);
    }

    public UUID sendTransactionToTopic(MakeTransactionDto makeTransactionDto) {
        InputTransactionDto transactionDto = convertToKafkaObject(makeTransactionDto);
        UUID correlationId = UUID.randomUUID();
        transactionDto.setCorrelationId(correlationId);

        log.info("Attempt to send new object to kafka: {}", correlationId);

        boolean acquired = transactionSemaphore.tryAcquire();
        if (!acquired) {
            log.warn("Transaction limit exceeded. Sending to DLQ. Correlation ID: {}", correlationId);

            // Log rate limit exceeded
            transactionLogService.log(
                    correlationId,
                    "WARN",
                    "KAFKA_PRODUCER",
                    "Rate limit exceeded, transaction sent to DLQ",
                    String.format("{\"maxConcurrent\":%d,\"dlqTopic\":\"%s\"}", maxConcurrent, inputTransactionsDlq)
            );

            sendToDlq(transactionDto, "RATE_LIMIT_EXCEEDED");
            throw new RequestLimitException("Request limit exceeded.");
        }

        // Track submitted transaction and update queue size
        metricsService.incrementTransactionsSubmitted();
        metricsService.setQueueSize(maxConcurrent - transactionSemaphore.availablePermits());

        CompletableFuture<SendResult<String, InputTransactionDto>> future =
                inputKafkaTemplate.send(inputTopic, correlationId.toString(), transactionDto);

        // Асинхронная обработка результата
        future.whenComplete((result, exception) -> {
            transactionSemaphore.release();
            metricsService.setQueueSize(maxConcurrent - transactionSemaphore.availablePermits());

            if (exception == null) {
                log.info("Successfully sent to Kafka. Topic: {}, Partition: {}, Offset: {}, correlationId: {}",
                        inputTopic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        correlationId);

                // Log successful Kafka send
                transactionLogService.log(
                        correlationId,
                        "INFO",
                        "KAFKA_PRODUCER",
                        "Transaction sent to Kafka topic",
                        String.format("{\"topic\":\"%s\",\"partition\":%d,\"offset\":%d}",
                                inputTopic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset())
                );
            } else {
                log.error("Failed to send corrId {} to Kafka topic: {}. Error: {}",
                        correlationId, inputTopic, exception.getMessage(), exception);

                // Log Kafka send failure
                transactionLogService.log(
                        correlationId,
                        "ERROR",
                        "KAFKA_PRODUCER",
                        "Failed to send transaction to Kafka",
                        String.format("{\"error\":\"%s\"}", exception.getMessage())
                );
            }
        });

        return correlationId;
    }

    private void sendToDlq(InputTransactionDto transactionDto, String reason) {
        try {
            log.info("Attempt to send transaction to DLQ, correlation id: {}", transactionDto.getCorrelationId());
            Map<String, Object> dlqMessage = Map.of(
                    "originalTransaction", transactionDto,
                    "dlqReason", reason,
                    "timestamp", Instant.now().toString()
            );

            dlqKafkaTemplate.send(inputTransactionsDlq, transactionDto.getCorrelationId().toString(), dlqMessage)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Sent to DLQ. Correlation ID: {}", transactionDto.getCorrelationId());
                        } else {
                            log.error("Failed to send to DLQ. Correlation ID: {}",
                                    transactionDto.getCorrelationId(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Critical error sending corrId " + transactionDto.getCorrelationId() +
                    " to DLQ " + e.getMessage());
        }
    }


    private InputTransactionDto convertToKafkaObject(MakeTransactionDto makeTransactionDto) {
        InputTransactionDto inputTransactionDto = new InputTransactionDto();

        log.info("Attempt to convert input transaction to kafka-object...");
        //Major
        inputTransactionDto.setSourceId(makeTransactionDto.getFrom());
        inputTransactionDto.setDestinationId(makeTransactionDto.getTo());
        inputTransactionDto.setAmount(makeTransactionDto.getAmount());
        inputTransactionDto.setTimeStamp(makeTransactionDto.getTimeStamp());

        //Optional
        inputTransactionDto.setCurrency(makeTransactionDto.getCurrency());
        inputTransactionDto.setChannel(makeTransactionDto.getChannel());
        inputTransactionDto.setGeo(makeTransactionDto.getGeo());
        inputTransactionDto.setDescription(makeTransactionDto.getDescription());

        log.info("Converting passed successfully!");
        return inputTransactionDto;
    }
}
