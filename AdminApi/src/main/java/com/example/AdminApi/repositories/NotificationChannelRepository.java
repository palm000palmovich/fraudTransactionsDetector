package com.example.AdminApi.repositories;

import com.example.AdminApi.enums.ChannelType;
import com.example.AdminApi.models.NotificationChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationChannelRepository extends JpaRepository<NotificationChannelEntity, Long> {
    Optional<NotificationChannelEntity> findByChannelType(ChannelType channelType);
}
