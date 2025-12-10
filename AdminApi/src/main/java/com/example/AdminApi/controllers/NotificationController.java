package com.example.AdminApi.controllers;

import com.example.AdminApi.component.TelegramBotManager;
import com.example.AdminApi.dto.*;
import com.example.AdminApi.models.AlertMessageEntity;
import com.example.AdminApi.enums.ChannelType;
import com.example.AdminApi.models.ChatEntity;
import com.example.AdminApi.repositories.AlertMessageRepository;
import com.example.AdminApi.repositories.ChatRepository;
import com.example.AdminApi.services.NotificationChannelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for notification management.
 *
 * Access control: Only ADMIN role
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationChannelService notificationChannelService;
    private final ChatRepository chatRepository;
    private final AlertMessageRepository alertMessageRepository;
    private final TelegramBotManager telegramBotManager;
    private final ObjectMapper objectMapper;

    /**
     * GET /api/notifications/channels
     * Get all notification channels with status
     */
    @GetMapping("/channels")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIEWER')")
    public ResponseEntity<List<NotificationChannelDto>> getAllChannels() {
        log.info("GET /api/notifications/channels");
        List<NotificationChannelDto> channels = notificationChannelService.getAllChannels();
        return ResponseEntity.ok(channels);
    }

    /**
     * PATCH /api/notifications/channels/{id}/toggle
     * Toggle channel enabled/disabled
     */
    @PatchMapping("/channels/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationChannelDto> toggleChannel(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> request) {

        Boolean enabled = request.get("enabled");
        log.info("PATCH /api/notifications/channels/{}/toggle - enabled={}", id, enabled);

        NotificationChannelDto updated = notificationChannelService.toggleChannel(id, enabled);
        return ResponseEntity.ok(updated);
    }

    /**
     * PATCH /api/notifications/channels/{id}/config
     * Update channel configuration (e.g., webhook URL)
     */
    @PatchMapping("/channels/{id}/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateChannelConfig(
            @PathVariable Long id,
            @RequestBody Map<String, String> configUpdate) {

        log.info("PATCH /api/notifications/channels/{}/config", id);

        try {
            notificationChannelService.updateChannelConfigFromMap(id, configUpdate);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Channel configuration updated");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to update channel config: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to update config: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * PUT /api/notifications/channels/telegram/config
     * Update Telegram bot configuration (restart bot with new token)
     */
    @PutMapping("/channels/telegram/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateTelegramConfig(
            @Valid @RequestBody TelegramConfigDto config) {

        log.info("PUT /api/notifications/channels/telegram/config");

        try {
            // Build config JSON
            Map<String, String> configMap = new HashMap<>();
            configMap.put("botToken", config.getBotToken());
            if (config.getBotUsername() != null) {
                configMap.put("botUsername", config.getBotUsername());
            }

            String configJson = objectMapper.writeValueAsString(configMap);

            // Update in database
            notificationChannelService.updateChannelConfigByType(ChannelType.TELEGRAM, configJson);

            // Restart bot with new token
            telegramBotManager.restartBot(config.getBotToken());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Telegram bot configuration updated and restarted");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to update Telegram config: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to update Telegram config: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * GET /api/notifications/telegram/chats
     * Get all Telegram chats (subscribers)
     */
    @GetMapping("/telegram/chats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIEWER')")
    public ResponseEntity<List<ChatDto>> getTelegramChats() {
        log.info("GET /api/notifications/telegram/chats");

        List<ChatDto> chats = chatRepository.findAll().stream()
                .map(this::convertToChatDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(chats);
    }

    /**
     * PATCH /api/notifications/telegram/chats/{id}/deactivate
     * Deactivate (block) a Telegram chat
     */
    @PatchMapping("/telegram/chats/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChatDto> deactivateChat(@PathVariable Long id) {
        log.info("PATCH /api/notifications/telegram/chats/{}/deactivate", id);

        ChatEntity chat = chatRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Chat not found: " + id));

        chat.setActive(false);
        ChatEntity updated = chatRepository.save(chat);

        return ResponseEntity.ok(convertToChatDto(updated));
    }

    /**
     * PATCH /api/notifications/telegram/chats/{id}/activate
     * Activate a Telegram chat
     */
    @PatchMapping("/telegram/chats/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChatDto> activateChat(@PathVariable Long id) {
        log.info("PATCH /api/notifications/telegram/chats/{}/activate", id);

        ChatEntity chat = chatRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Chat not found: " + id));

        chat.setActive(true);
        ChatEntity updated = chatRepository.save(chat);

        return ResponseEntity.ok(convertToChatDto(updated));
    }

    /**
     * GET /api/notifications/alerts
     * Get alert history with pagination
     */
    @GetMapping("/alerts")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIEWER')")
    public ResponseEntity<PagedResponse<AlertHistoryDto>> getAlertHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "desc") String sort) {

        log.info("GET /api/notifications/alerts - page={}, size={}, sort={}", page, size, sort);

        // Create pageable with sort by sendingTime
        Sort sortOrder = sort.equalsIgnoreCase("asc")
                ? Sort.by("sendingTime").ascending()
                : Sort.by("sendingTime").descending();

        Pageable pageable = PageRequest.of(page, size, sortOrder);

        // Fetch from database
        Page<AlertMessageEntity> alertPage = alertMessageRepository.findAll(pageable);

        // Convert to DTOs
        List<AlertHistoryDto> alerts = alertPage.getContent().stream()
                .map(this::convertToAlertDto)
                .collect(Collectors.toList());

        // Build paged response
        PagedResponse<AlertHistoryDto> response = new PagedResponse<>(
                alerts,
                page,
                size,
                alertPage.getTotalElements(),
                alertPage.getTotalPages(),
                alertPage.isFirst(),
                alertPage.isLast()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/notifications/stats
     * Get notification statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIEWER')")
    public ResponseEntity<Map<String, Object>> getNotificationStats() {
        log.info("GET /api/notifications/stats");

        long totalAlerts = alertMessageRepository.count();
        int activeTelegramChats = chatRepository.findByActiveTrue().size();
        int totalTelegramChats = (int) chatRepository.count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAlertsSent", totalAlerts);
        stats.put("activeTelegramChats", activeTelegramChats);
        stats.put("totalTelegramChats", totalTelegramChats);

        return ResponseEntity.ok(stats);
    }

    // Helper methods

    private ChatDto convertToChatDto(ChatEntity entity) {
        return new ChatDto(
                entity.getId(),
                entity.getChatId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getUsername(),
                entity.isActive(),
                entity.getRegisteredAt(),
                entity.getLastNotifiedAt()
        );
    }

    private AlertHistoryDto convertToAlertDto(AlertMessageEntity entity) {
        return new AlertHistoryDto(
                entity.getId(),
                entity.getRuleID(),
                entity.getRuleName(),
                entity.getReason(),
                entity.getSendingTime(),
                entity.getTransactionCorrelationId()
        );
    }
}
