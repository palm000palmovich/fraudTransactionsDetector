package com.example.AdminApi.services;

import com.example.AdminApi.component.AlertWebSocketHandler;
import com.example.AdminApi.dto.MessageAlertDto;
import com.example.AdminApi.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final AlertWebSocketHandler alertWebSocketHandler;
    private final NotificationChannelService notificationChannelService;

    @Async
    public void sendWebSocketAlert(MessageAlertDto alertDto) {
        // Check if WebSocket channel is enabled
        if (!notificationChannelService.isChannelEnabled(ChannelType.WEBSOCKET)) {
            log.debug("WebSocket channel is disabled, skipping alert");
            return;
        }

        try {
            log.info("Sending alert via WebSocket to {} active connections",
                    alertWebSocketHandler.getActiveConnectionsCount());

            alertWebSocketHandler.broadcastAlert(alertDto);

        } catch (Exception e) {
            log.error("Error sending alert via WebSocket: {}", e.getMessage());
        }
    }
}
