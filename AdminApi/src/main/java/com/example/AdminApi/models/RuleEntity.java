package com.example.AdminApi.models;

import com.example.AdminApi.enums.RuleType;
import com.example.AdminApi.ruleParametres.RuleParams;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//@Entity
@Table(name = "Rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "rule_type")
    @Enumerated(EnumType.STRING)
    private RuleType ruleType;
    @Column(name = "name")
    private String name;
    @Column(name = "priority")
    private int priority;
    private boolean enabled = true;

    @Column(columnDefinition = "JSON")
    private String paramsJson;

    @Transient
    private RuleParams params;

    // Преобразуем JSON в объект после загрузки из БД
//    @PostLoad
//    private void parseParams() {
//        this.params = ObjectMapperUtils.parse(paramsJson, RuleParams.class);
//    }
//
//    // Преобразуем объект в JSON перед сохранением в БД
//    @PrePersist
//    @PreUpdate
//    private void serializeParams() {
//        this.paramsJson = ObjectMapperUtils.serialize(params);
//    }


}
