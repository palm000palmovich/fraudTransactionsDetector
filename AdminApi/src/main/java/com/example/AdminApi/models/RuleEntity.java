package com.example.AdminApi.models;

import com.example.AdminApi.enums.RuleType;
import com.example.AdminApi.ruleParametres.RuleParams;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "rules")
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

    // Versioning
    @Column(name = "version")
    private Integer version = 1;

    // Timestamps
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Audit fields
    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    // Automatically set timestamps
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

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
