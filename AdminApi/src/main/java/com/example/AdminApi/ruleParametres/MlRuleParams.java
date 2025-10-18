package com.example.AdminApi.ruleParametres;


import com.example.AdminApi.enums.RuleType;

public class MlRuleParams extends RuleParams {
    private double threshold;   //Порог

    public MlRuleParams() {
        this.type = RuleType.ML;
    }
}
