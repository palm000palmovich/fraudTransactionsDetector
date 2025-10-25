package com.example.AdminApi.repositories;

import com.example.AdminApi.models.TransactionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long>, JpaSpecificationExecutor<TransactionLog> {

    // Find all logs for a specific transaction (ordered by timestamp)
    List<TransactionLog> findByCorrelationIdOrderByTimestampAsc(UUID correlationId);

    // Find logs by level
    List<TransactionLog> findByLevel(String level);

    // Find logs by component
    List<TransactionLog> findByComponent(String component);
}
