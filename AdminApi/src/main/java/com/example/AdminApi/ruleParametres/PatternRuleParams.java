package com.example.AdminApi.ruleParametres;

import com.example.AdminApi.enums.RuleType;

import java.math.BigDecimal;
import java.time.Duration;

public class PatternRuleParams extends RuleParams {
    private Duration timeWindow;
    private int transactionLimit;
    private BigDecimal maxAmount;
    private String patternType;

    public PatternRuleParams() {
        this.type = RuleType.PATTERN;
    }
}
