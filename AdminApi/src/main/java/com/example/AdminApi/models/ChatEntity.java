package com.example.AdminApi.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chats")
@NoArgsConstructor
@AllArgsConstructor
public class ChatEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String chatId;
    private String firstName;
    private String lastName;
    private String username;
    private boolean active = true;
    private LocalDateTime registeredAt;
    private LocalDateTime lastNotifiedAt;
}
