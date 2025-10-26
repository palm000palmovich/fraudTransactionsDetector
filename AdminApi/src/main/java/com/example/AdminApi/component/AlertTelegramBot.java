package com.example.AdminApi.component;

import com.example.AdminApi.dto.MessageAlertDto;
import com.example.AdminApi.models.ChatEntity;
import com.example.AdminApi.repositories.ChatRepository;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class AlertTelegramBot extends TelegramLongPollingBot {
    private final String botToken;
    private final ChatRepository chatRepository;

    public AlertTelegramBot(@Value("${app.telegram.bot.token}") String botToken,
                            ChatRepository chatRepository) {
        super(botToken);
        this.botToken = botToken;
        this.chatRepository = chatRepository;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            String text = message.getText();
            String chatId = message.getChatId().toString();

            if ("/start".equals(text)) {
                registerUser(chatId, message.getFrom());
                sendWelcomeMessage(chatId);
            } else if ("/stop".equals(text)) {
                unregisterUser(chatId);
                sendGoodbyeMessage(chatId);
            } else if ("/status".equals(text)) {
                sendStatusMessage(chatId);
            }
        }
    }

    private void registerUser(String chatId, User user) {
        try {
            ChatEntity chatEntity = chatRepository.findByChatId(chatId)
                    .orElse(new ChatEntity());

            chatEntity.setChatId(chatId);
            chatEntity.setFirstName(user.getFirstName());
            chatEntity.setLastName(user.getLastName());
            chatEntity.setUsername(user.getUserName());
            chatEntity.setActive(true);
            chatEntity.setRegisteredAt(LocalDateTime.now());

            chatRepository.save(chatEntity);
            log.info("User registered: {} ({})", chatId, user.getUserName());

        } catch (Exception e) {
            log.error("Failed to register user {}: {}", chatId, e.getMessage());
        }
    }

    private void unregisterUser(String chatId) {
        try {
            chatRepository.findByChatId(chatId).ifPresent(chat -> {
                chat.setActive(false);
                chatRepository.save(chat);
                log.info("User unregistered: {}", chatId);
            });
        } catch (Exception e) {
            log.error("Failed to unregister user {}: {}", chatId, e.getMessage());
        }
    }

    private void sendWelcomeMessage(String chatId) {
        try {
            String text = """
                    🚨 <b>Alert System Registered</b> 🚨
                    
                    You have successfully registered for alert notifications!
                    
                    You will receive real-time alerts about suspicious transactions.
                    
                    Commands:
                    /status - Check your subscription status
                    /stop - Stop receiving alerts
                    """;

            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode("HTML")
                    .build();

            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send welcome message: {}", e.getMessage());
        }
    }

    private void sendGoodbyeMessage(String chatId) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text("✅ You have been unsubscribed from alert notifications.")
                    .build();
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send goodbye message: {}", e.getMessage());
        }
    }

    private void sendStatusMessage(String chatId) {
        try {
            String status = chatRepository.findByChatId(chatId)
                    .map(chat -> chat.isActive() ? "✅ ACTIVE" : "❌ INACTIVE")
                    .orElse("❌ NOT REGISTERED");

            String text = String.format("""
                    📊 <b>Subscription Status</b>
                    
                    Status: %s
                    Chat ID: %s
                    """, status, chatId);

            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode("HTML")
                    .build();

            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send status message: {}", e.getMessage());
        }
    }

    public void sendAlert(MessageAlertDto alert) {
        List<ChatEntity> activeChats = chatRepository.findByActiveTrue();

        if (activeChats.isEmpty()) {
            log.warn("No active chats found for sending alerts");
            return;
        }

        String message = formatAlertMessage(alert);

        activeChats.forEach(chat -> {
            try {
                SendMessage sendMessage = SendMessage.builder()
                        .chatId(chat.getChatId())
                        .text(message)
                        .parseMode("HTML")
                        .build();

                execute(sendMessage);

                // Обновляем время последнего уведомления
                chat.setLastNotifiedAt(LocalDateTime.now());
                chatRepository.save(chat);

                log.info("Alert sent to chat: {} ({})", chat.getChatId(), chat.getUsername());

            } catch (TelegramApiException e) {
                log.error("Failed to send alert to chat {}: {}", chat.getChatId(), e.getMessage());
            }
        });
    }

    private String formatAlertMessage(MessageAlertDto alert) {
        return String.format("""
            🚨 <b>ALERT TRIGGERED</b> 🚨
            
            <b>Rule:</b> %s
            <b>Reason:</b> %s
            <b>Time:</b> %s
            <b>Transaction ID:</b> <code>%s</code>
            
            Please review this transaction immediately.
            """,
                alert.getRuleName(),
                alert.getReason(),
                alert.getSendingTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                alert.getTransactionCorrelationId()
        );
    }

    @Override
    public String getBotUsername() {
        return "AlertBot";
    }
}
