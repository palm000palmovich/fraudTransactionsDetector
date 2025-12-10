package com.example.AdminApi.dto;

import com.example.AdminApi.enums.ChannelType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationChannelDto {
    private Long id;
    private ChannelType channelType;
    private Boolean enabled;

    @JsonIgnore
    private String configJson;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Get configuration as Map for JSON serialization
     */
    public Map<String, Object> getConfiguration() {
        if (configJson == null || configJson.isEmpty() || configJson.equals("{}")) {
            return new HashMap<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
