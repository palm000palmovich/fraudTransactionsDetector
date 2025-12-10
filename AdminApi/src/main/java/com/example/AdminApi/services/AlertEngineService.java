package com.example.AdminApi.services;

import com.example.AdminApi.component.AlertTelegramBot;
import com.example.AdminApi.dto.MessageAlertDto;
import com.example.AdminApi.models.AlertMessageEntity;
import com.example.AdminApi.repositories.AlertMessageRepository;
import com.example.AdminApi.repositories.ChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEngineService {
    private final AlertMessageRepository alertMessageRepository;
    private final AlertTelegramBot telegramBot;
    private final ChatRepository chatRepository;
    private final WebhookService webhookService;

    @Async
    public void sendAlertToUser(MessageAlertDto messageAlertDto) {
        log.info("Processing alert: {}", messageAlertDto.getRuleName());

        // Сохраняем в базу данных
        saveAlertToDatabase(messageAlertDto);

        // Отправляем в Telegram всем активным пользователям
        sendTelegramAlert(messageAlertDto);

        // Отправляем вебхук
        sendWebhookAlert(messageAlertDto);
    }

    private void saveAlertToDatabase(MessageAlertDto alert) {
        try {
            AlertMessageEntity entity = convertToEntity(alert);
            alertMessageRepository.save(entity);
            log.info("Alert saved to database");
        } catch (Exception e) {
            log.error("Failed to save alert to database: {}", e.getMessage());
        }
    }

    private void sendTelegramAlert(MessageAlertDto alert) {
        try {
            int activeUsersCount = chatRepository.findByActiveTrue().size();
            log.info("Sending alert to {} active users", activeUsersCount);

            telegramBot.sendAlert(alert);

        } catch (Exception e) {
            log.error("Failed to send Telegram alert: {}", e.getMessage());
        }
    }

    private void sendWebhookAlert(MessageAlertDto alert) {
        try {
            webhookService.sendWebhookAlert(alert);
        } catch (Exception e) {
            log.error("Failed to send webhook alert: {}", e.getMessage());
        }
    }

    private AlertMessageEntity convertToEntity(MessageAlertDto dto) {
        AlertMessageEntity entity = new AlertMessageEntity();
        entity.setRuleID(dto.getRuleID());
        entity.setRuleName(dto.getRuleName());
        entity.setReason(dto.getReason());
        entity.setSendingTime(dto.getSendingTime());
        entity.setTransactionCorrelationId(dto.getTransactionCorrelationId());
        entity.setCreatedAt(Instant.now());
        return entity;
    }
}
