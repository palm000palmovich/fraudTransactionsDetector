package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Component
@NoArgsConstructor
@AllArgsConstructor
public class InputTransactionDto {
    private Long sourceId;
    private Long destinationId;
    private double amount;
    private String currency;
    private LocalDateTime timeStamp;
    private UUID corelationId;
}
