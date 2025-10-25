package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    private Long id;
    private String timestamp; // ISO 8601 format
    private String username;
    private String action; // CREATE, UPDATE, DELETE, TOGGLE
    private Long ruleId;
    private String ruleName;
    private String details;
}
