package com.example.AdminApi.repositories;

import com.example.AdminApi.models.AlertMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertMessageRepository extends JpaRepository<AlertMessageEntity, Long> {
}
