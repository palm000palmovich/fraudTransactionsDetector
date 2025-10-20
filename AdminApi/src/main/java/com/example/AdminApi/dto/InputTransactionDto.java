package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class InputTransactionDto {
    private Long sourceId;
    private Long destinationId;
    private double amount;
    private LocalDateTime timeStamp;

    private String currency;
    private String channel;
    private String geo;
    private String description;

    private UUID correlationId;
}
