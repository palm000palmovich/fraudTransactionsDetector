package com.example.AdminApi.services;

import com.example.AdminApi.dto.InputTransactionDto;
import com.example.AdminApi.dto.MakeTransactionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final KafkaTemplate<String, InputTransactionDto> kafkaTemplate;

    @Value("${app.kafka.topic.input-transactions}")
    private String inputTopic;

    public UUID sendTransactionToTopic(MakeTransactionDto makeTransactionDto) {
        InputTransactionDto transactionDto = convertToKafkaObject(makeTransactionDto);
        UUID correlationId = UUID.randomUUID();
        transactionDto.setCorrelationId(correlationId);

        log.info("Attempt to send new object to kafka: {}", transactionDto);

        CompletableFuture<SendResult<String, InputTransactionDto>> future =
                kafkaTemplate.send(inputTopic, correlationId.toString(), transactionDto);

        // Асинхронная обработка результата
        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("Successfully sent to Kafka. Topic: {}, Partition: {}, Offset: {}",
                        inputTopic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send to Kafka topic: {}. Error: {}",
                        inputTopic, exception.getMessage(), exception);
            }
        });

        return correlationId;
    }


    private InputTransactionDto convertToKafkaObject(MakeTransactionDto makeTransactionDto) {
        InputTransactionDto inputTransactionDto = new InputTransactionDto();

        log.info("Attempt to convert input transaction to kafka-object...");
        //Major
        inputTransactionDto.setSourceId(makeTransactionDto.getSourceId());
        inputTransactionDto.setDestinationId(makeTransactionDto.getDestinationId());
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
