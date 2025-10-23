package com.example.AdminApi.dto;

import com.example.AdminApi.enums.RuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for returning Rule data to Admin Panel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDto {
    private Long id;
    private String name;
    private RuleType ruleType;
    private String paramsJson;
    private Boolean enabled;
    private Integer priority;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
