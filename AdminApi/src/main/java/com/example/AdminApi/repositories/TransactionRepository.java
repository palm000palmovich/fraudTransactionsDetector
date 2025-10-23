package com.example.AdminApi.repositories;

import com.example.AdminApi.models.TransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    // Find by correlation ID (for idempotency checks and tracing)
    Optional<TransactionEntity> findByCorrelationId(UUID correlationId);

    // Find by status (for processing queues: PENDING, PROCESSED, ALERTED, REVIEWED)
    List<TransactionEntity> findByStatus(String status);

    // Find transactions from specific source after a certain time
    // Used for PATTERN rules (e.g., rapid small transfers from same source)
    List<TransactionEntity> findBySourceIdAndTimestampAfter(String sourceId, LocalDateTime timestamp);

    // Find transactions by triggered rule (for analytics and rule effectiveness)
    List<TransactionEntity> findByTriggeredRuleId(Long triggeredRuleId);

    // Find alerted transactions (status = ALERTED) for analyst review
    List<TransactionEntity> findByStatusOrderByCreatedAtDesc(String status);

    // Search transactions by correlation ID (for Admin Panel search)
    // Use custom query to convert UUID to string for searching
    @Query("SELECT t FROM TransactionEntity t WHERE LOWER(CAST(t.correlationId AS string)) LIKE LOWER(CONCAT('%', :correlationId, '%'))")
    List<TransactionEntity> findByCorrelationIdContainingIgnoreCase(@Param("correlationId") String correlationId);

    // Count transactions by status (for Admin Panel statistics)
    long countByStatus(String status);

    // Complex filtering for Admin Panel with pagination
    @Query("SELECT t FROM TransactionEntity t WHERE " +
           "(:status IS NULL OR :status = '' OR t.status = :status) AND " +
           "(:correlationId IS NULL OR :correlationId = '' OR LOWER(CAST(t.correlationId AS string)) LIKE LOWER(CONCAT('%', :correlationId, '%'))) AND " +
           "(:sourceId IS NULL OR :sourceId = '' OR t.sourceId = :sourceId) AND " +
           "(:destinationId IS NULL OR :destinationId = '' OR t.destinationId = :destinationId) AND " +
           "(t.timestamp >= :dateFrom) AND " +
           "(t.timestamp <= :dateTo)")
    Page<TransactionEntity> findByFilters(
            @Param("status") String status,
            @Param("correlationId") String correlationId,
            @Param("sourceId") String sourceId,
            @Param("destinationId") String destinationId,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );
}
