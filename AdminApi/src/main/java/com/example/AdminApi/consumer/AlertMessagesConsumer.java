package com.example.AdminApi.consumer;

import com.example.AdminApi.dto.MessageAlertDto;
import com.example.AdminApi.services.AlertEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertMessagesConsumer {
    private final AlertEngineService alertEngineService;

    @KafkaListener(
            topics = "${app.kafka.topic.alerted-transactions}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeAlertMessages(MessageAlertDto messageAlertDto) {
        try {
            log.info("Received alert message: {}", messageAlertDto.toString());
            alertEngineService.sendAlertToUser(messageAlertDto);
        } catch (Exception ex) {
            log.error("Error reading from alert topic: {}]", ex.getMessage());
        }
    }

}
