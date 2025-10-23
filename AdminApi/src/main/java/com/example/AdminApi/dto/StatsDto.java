package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for transaction statistics in Admin Panel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsDto {
    private Long totalTransactions;
    private Long processedCount;
    private Long alertedCount;
    private Long pendingCount;
}
