package com.example.AdminApi.repositories;

import com.example.AdminApi.models.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    // Find all logs for a specific rule (ordered by timestamp descending)
    List<AuditLog> findByRuleIdOrderByTimestampDesc(Long ruleId);

    // Find logs by action type
    List<AuditLog> findByAction(String action);

    // Find logs by username
    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);
}
