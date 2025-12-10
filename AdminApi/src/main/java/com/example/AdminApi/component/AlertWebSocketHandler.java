package com.example.AdminApi.component;

import com.example.AdminApi.dto.MessageAlertDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.*;

@Component
@Slf4j
public class AlertWebSocketHandler extends TextWebSocketHandler {
    private static final Set<WebSocketSession> sessions = Collections.synchronizedSet(new HashSet<>());

    private ObjectMapper objectMapper;


    // Внедряем ObjectMapper через конструктор
    public AlertWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connection established: {}", session.getId());
        log.info("Total active WebSocket connections: {}", sessions.size());

        // Отправляем приветственное сообщение
        String welcomeMsg = "{\"type\":\"connected\",\"message\":\"WebSocket connected successfully\"}";
        session.sendMessage(new TextMessage(welcomeMsg));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
        log.info("Total active WebSocket connections: {}", sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: {}", exception.getMessage());
        sessions.remove(session);
    }

    // Метод для отправки алертов всем подключенным клиентам
    public void broadcastAlert(MessageAlertDto alert) {
        synchronized (sessions) {
            if (sessions.isEmpty()) {
                log.debug("No active WebSocket connections to broadcast alert");
                return;
            }

            try {
                String message = convertAlertToJson(alert);
                int sentCount = 0;

                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(new TextMessage(message));
                            sentCount++;
                        } catch (Exception e) {
                            log.error("Error sending message to session {}: {}", session.getId(), e.getMessage());
                        }
                    }
                }

                log.info("Broadcasted alert to {}/{} active WebSocket connections", sentCount, sessions.size());

            } catch (Exception e) {
                log.error("Error broadcasting alert: {}", e.getMessage());
            }
        }
    }

    private String convertAlertToJson(MessageAlertDto alert) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "fraud_alert");
            message.put("timestamp", Instant.now().toString());
            message.put("data", alert);

            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Error converting alert to JSON: {}", e.getMessage());
            return "{\"type\":\"error\",\"message\":\"Failed to serialize alert\"}";
        }
    }

    // Получить количество активных подключений
    public int getActiveConnectionsCount() {
        return sessions.size();
    }
}
