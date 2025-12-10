package com.example.AdminApi.services;

import com.example.AdminApi.dto.NotificationChannelDto;
import com.example.AdminApi.enums.ChannelType;
import com.example.AdminApi.models.NotificationChannelEntity;
import com.example.AdminApi.repositories.NotificationChannelRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationChannelService {

    private final NotificationChannelRepository notificationChannelRepository;
    private final ObjectMapper objectMapper;

    /**
     * Get all notification channels
     */
    public List<NotificationChannelDto> getAllChannels() {
        return notificationChannelRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Toggle channel enabled/disabled
     */
    @Transactional
    public NotificationChannelDto toggleChannel(Long id, Boolean enabled) {
        NotificationChannelEntity channel = notificationChannelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Channel not found: " + id));

        log.info("Toggling channel {} from {} to {}", channel.getChannelType(), channel.getEnabled(), enabled);
        channel.setEnabled(enabled);
        NotificationChannelEntity updated = notificationChannelRepository.save(channel);

        return toDto(updated);
    }

    /**
     * Update channel configuration (JSON)
     */
    @Transactional
    public NotificationChannelDto updateChannelConfig(Long id, String configJson) {
        NotificationChannelEntity channel = notificationChannelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Channel not found: " + id));

        log.info("Updating config for channel {}", channel.getChannelType());
        channel.setConfigJson(configJson);
        NotificationChannelEntity updated = notificationChannelRepository.save(channel);

        return toDto(updated);
    }

    /**
     * Update channel configuration from Map
     */
    @Transactional
    public NotificationChannelDto updateChannelConfigFromMap(Long id, Map<String, String> configUpdate) {
        NotificationChannelEntity channel = notificationChannelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Channel not found: " + id));

        log.info("Updating config for channel {} with: {}", channel.getChannelType(), configUpdate);

        // Convert Map to JSON
        try {
            String configJson = objectMapper.writeValueAsString(configUpdate);
            channel.setConfigJson(configJson);
            NotificationChannelEntity updated = notificationChannelRepository.save(channel);
            return toDto(updated);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize config: " + e.getMessage(), e);
        }
    }

    /**
     * Update channel configuration by type
     */
    @Transactional
    public NotificationChannelDto updateChannelConfigByType(ChannelType channelType, String configJson) {
        NotificationChannelEntity channel = notificationChannelRepository.findByChannelType(channelType)
                .orElseThrow(() -> new RuntimeException("Channel not found: " + channelType));

        log.info("Updating config for channel type {}", channelType);
        channel.setConfigJson(configJson);
        NotificationChannelEntity updated = notificationChannelRepository.save(channel);

        return toDto(updated);
    }

    /**
     * Check if channel is enabled
     */
    public boolean isChannelEnabled(ChannelType channelType) {
        return notificationChannelRepository.findByChannelType(channelType)
                .map(NotificationChannelEntity::getEnabled)
                .orElse(false);
    }

    /**
     * Get channel configuration
     */
    public String getChannelConfig(ChannelType channelType) {
        return notificationChannelRepository.findByChannelType(channelType)
                .map(NotificationChannelEntity::getConfigJson)
                .orElse("{}");
    }

    /**
     * Get channel by type
     */
    public NotificationChannelDto getChannelByType(ChannelType channelType) {
        return notificationChannelRepository.findByChannelType(channelType)
                .map(this::toDto)
                .orElseThrow(() -> new RuntimeException("Channel not found: " + channelType));
    }

    private NotificationChannelDto toDto(NotificationChannelEntity entity) {
        return new NotificationChannelDto(
                entity.getId(),
                entity.getChannelType(),
                entity.getEnabled(),
                entity.getConfigJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
