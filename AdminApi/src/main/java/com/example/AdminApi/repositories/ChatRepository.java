package com.example.AdminApi.repositories;

import com.example.AdminApi.models.ChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<ChatEntity, Long> {
    List<ChatEntity> findByActiveTrue();
    Optional<ChatEntity> findByChatId(String chatId);
}
