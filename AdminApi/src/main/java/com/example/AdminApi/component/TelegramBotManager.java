package com.example.AdminApi.component;

import com.example.AdminApi.dto.MessageAlertDto;
import com.example.AdminApi.enums.ChannelType;
import com.example.AdminApi.repositories.ChatRepository;
import com.example.AdminApi.services.NotificationChannelService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotManager {

    private final ChatRepository chatRepository;
    private final NotificationChannelService notificationChannelService;
    private final ObjectMapper objectMapper;

    private TelegramBotsApi telegramBotsApi;
    private AlertTelegramBot currentBot;

    @PostConstruct
    public void init() {
        try {
            telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Load bot config from database and start
            String config = notificationChannelService.getChannelConfig(ChannelType.TELEGRAM);
            JsonNode configNode = objectMapper.readTree(config);
            String botToken = configNode.get("botToken").asText();

            startBot(botToken);
            log.info("✅ TelegramBotManager initialized and bot started");

        } catch (Exception e) {
            log.error("❌ Failed to initialize TelegramBotManager: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        stopBot();
        log.info("TelegramBotManager shutdown completed");
    }

    /**
     * Start Telegram bot with given token
     */
    public synchronized void startBot(String botToken) {
        try {
            stopBot();

            currentBot = new AlertTelegramBot(botToken, chatRepository);
            telegramBotsApi.registerBot(currentBot);

            log.info("✅ Telegram bot started successfully");

        } catch (TelegramApiException e) {
            log.error("❌ Failed to start Telegram bot: {}", e.getMessage());
            throw new RuntimeException("Failed to start Telegram bot: " + e.getMessage());
        }
    }

    /**
     * Stop current bot
     */
    public synchronized void stopBot() {
        if (currentBot != null) {
            try {
                // Close the bot session gracefully
                if (currentBot != null) {
                    currentBot.onClosing();
                }
                currentBot = null;
                log.info("✅ Telegram bot stopped successfully");
            } catch (Exception e) {
                log.error("⚠️ Error stopping Telegram bot: {}", e.getMessage());
                currentBot = null; // Set to null anyway
            }
        }
    }

    /**
     * Restart bot with new token
     */
    public void restartBot(String newBotToken) {
        log.info("🔄 Restarting Telegram bot with new token");
        startBot(newBotToken);
    }

    /**
     * Send alert to Telegram (checks if channel is enabled)
     */
    public void sendAlert(MessageAlertDto alert) {
        if (!notificationChannelService.isChannelEnabled(ChannelType.TELEGRAM)) {
            log.debug("Telegram channel is disabled, skipping alert");
            return;
        }

        if (currentBot == null) {
            log.warn("Telegram bot is not running, cannot send alert");
            return;
        }

        try {
            currentBot.sendAlert(alert);
        } catch (Exception e) {
            log.error("Failed to send Telegram alert: {}", e.getMessage());
        }
    }

    /**
     * Check if bot is running
     */
    public boolean isBotRunning() {
        return currentBot != null;
    }
}
