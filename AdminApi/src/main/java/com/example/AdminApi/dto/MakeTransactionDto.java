package com.example.AdminApi.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MakeTransactionDto {
    @NotNull(message = "From account is mandatory")
    @NotBlank(message = "From account cannot be blank")
    private String from;
    @NotNull(message = "To account is mandatory")
    @NotBlank(message = "To account cannot be blank")
    private String to;
    @NotNull(message = "Amount is mandatory")
    @Positive(message = "Amount must be positive")
    private double amount;
    @NotNull(message = "Timestamp is mandatory")
    @PastOrPresent(message = "Timestamp cannot be in the future")
    private LocalDateTime timeStamp;

    private String currency;
    private String channel;
    private String geo;
    private String description;
}
