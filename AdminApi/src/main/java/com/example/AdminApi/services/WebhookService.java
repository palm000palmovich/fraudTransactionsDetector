package com.example.AdminApi.services;

import com.example.AdminApi.component.AlertWebSocketHandler;
import com.example.AdminApi.dto.MessageAlertDto;
import com.example.AdminApi.dto.WebhookAlertDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AlertWebSocketHandler alertWebSocketHandler;

    @Value("${app.webhook.url}")
    private String webhookUrl;

    @Value("${app.webhook.enabled:false}")
    private boolean webhookEnabled;

    @Value("${app.webhook.retry.count:3}")
    private int retryCount;

    @Async
    public void sendWebhookAlert(MessageAlertDto alertDto) {
        if (!webhookEnabled) {
            log.debug("Webhook alerts are disabled");
            return;
        }

        // 1. Сначала отправляем через WebSocket (реальное время)
        sendViaWebSocket(alertDto);

        // 2. Затем отправляем HTTP вебхук (для совместимости)
        sendViaHttpWebhook(alertDto);
    }

    private void sendViaWebSocket(MessageAlertDto alertDto) {
        try {
            log.info("Sending alert via WebSocket to {} active connections",
                    alertWebSocketHandler.getActiveConnectionsCount());

            alertWebSocketHandler.broadcastAlert(alertDto);

        } catch (Exception e) {
            log.error("Error sending alert via WebSocket: {}", e.getMessage());
        }
    }

    private void sendViaHttpWebhook(MessageAlertDto alertDto) {
        WebhookAlertDto webhookAlert = convertToWebhookDto(alertDto);

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                log.info("Sending HTTP webhook alert (attempt {}/{}): {}",
                        attempt, retryCount, webhookUrl);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("User-Agent", "FraudDetectionService/1.0");

                HttpEntity<WebhookAlertDto> request = new HttpEntity<>(webhookAlert, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                        webhookUrl,
                        request,
                        String.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("HTTP webhook alert sent successfully");
                    return;
                } else {
                    log.warn("HTTP webhook returned status: {}", response.getStatusCode());
                }

            } catch (Exception e) {
                log.error("Failed to send HTTP webhook (attempt {}/{}): {}",
                        attempt, retryCount, e.getMessage());

                if (attempt < retryCount) {
                    try {
                        Thread.sleep(1000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("All attempts to send HTTP webhook failed");
    }

    private WebhookAlertDto convertToWebhookDto(MessageAlertDto dto) {
        WebhookAlertDto webhookDto = new WebhookAlertDto();
        webhookDto.setRuleID(dto.getRuleID());
        webhookDto.setRuleName(dto.getRuleName());
        webhookDto.setReason(dto.getReason());
        webhookDto.setSendingTime(dto.getSendingTime());
        webhookDto.setTransactionCorrelationId(dto.getTransactionCorrelationId());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serviceVersion", "1.0.0");
        metadata.put("environment", "production");
        metadata.put("deliveryMethod", "http_webhook");
        webhookDto.setMetadata(metadata);

        return webhookDto;
    }
}
