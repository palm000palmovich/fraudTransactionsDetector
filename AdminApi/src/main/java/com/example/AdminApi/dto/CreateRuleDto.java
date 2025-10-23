package com.example.AdminApi.dto;

import com.example.AdminApi.enums.RuleType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating new Rules from Admin Panel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRuleDto {

    @NotBlank(message = "Rule name is required")
    private String name;

    @NotNull(message = "Rule type is required")
    private RuleType ruleType;

    @NotBlank(message = "Parameters JSON is required")
    private String paramsJson;

    @NotNull(message = "Enabled status is required")
    private Boolean enabled;

    @NotNull(message = "Priority is required")
    @Min(value = 1, message = "Priority must be at least 1")
    private Integer priority;
}
