package com.example.AdminApi.repositories;

import com.example.AdminApi.models.RuleEntity;
import com.example.AdminApi.enums.RuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<RuleEntity, Long> {

    // *** CRITICAL for First-Match policy ***
    // Returns enabled rules sorted by priority ASC
    // Rule Engine will check them in order and stop at first match
    List<RuleEntity> findByEnabledTrueOrderByPriorityAsc();

    // Find rules by type (for analytics and management)
    List<RuleEntity> findByRuleType(RuleType ruleType);

    // Find all rules (enabled and disabled) sorted by priority
    List<RuleEntity> findAllByOrderByPriorityAsc();

    // Find enabled rules of specific type
    List<RuleEntity> findByEnabledTrueAndRuleTypeOrderByPriorityAsc(RuleType ruleType);
}
