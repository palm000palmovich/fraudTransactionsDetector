package com.example.AdminApi.ruleParametres;

import com.example.AdminApi.enums.RuleType;

public class ThresholdRuleParams extends RuleParams {
    private String field;
    private String operator;
    private Object value;

    public ThresholdRuleParams() {
        this.type = RuleType.THRESHOLD;
    }
}
