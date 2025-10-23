package com.example.AdminApi.dto;

import com.example.AdminApi.enums.RuleType;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating existing Rules from Admin Panel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRuleDto {

    private String name;
    private RuleType ruleType;
    private String paramsJson;
    private Boolean enabled;

    @Min(value = 1, message = "Priority must be at least 1")
    private Integer priority;
}
