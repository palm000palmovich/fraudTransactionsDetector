package com.example.AdminApi.configuration;

import com.example.AdminApi.component.AlertTelegramBot;
import com.example.AdminApi.repositories.ChatRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramBotConfig {

    @Value("${app.telegram.bot.token}")
    private String botToken;

    @Bean
    public TelegramBotsApi telegramBotsApi(AlertTelegramBot alertTelegramBot) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(alertTelegramBot);
            return botsApi;
        } catch (TelegramApiException e) {
            throw new RuntimeException("Failed to register telegram bot", e);
        }
    }

    @Bean
    public AlertTelegramBot alertTelegramBot(@Value("${app.telegram.bot.token}") String botToken,
                                             ChatRepository chatRepository) {
        return new AlertTelegramBot(botToken, chatRepository);
    }
}
