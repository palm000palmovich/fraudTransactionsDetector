package com.example.AdminApi.ruleParametres;

import com.example.AdminApi.enums.RuleType;

import java.util.List;

public class CompositeRuleParams extends RuleParams {
    private String logicalOperator; // "AND", "OR"
    private List<Long> childRuleIds; // ID дочерних правил

    public CompositeRuleParams() {
        this.type = RuleType.COMPOSITE;
    }
}
