package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatDto {
    private Long id;
    private String chatId;
    private String firstName;
    private String lastName;
    private String username;
    private Boolean active;
    private LocalDateTime registeredAt;
    private LocalDateTime lastNotifiedAt;
}
