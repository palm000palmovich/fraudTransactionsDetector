package com.example.AdminApi.controllers;

import com.example.AdminApi.services.RuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(path = "/rules")
@RequiredArgsConstructor
public class RuleController {
    private final RuleService ruleService;

    @PostMapping(path = "/save-new-rule")
    public ResponseEntity<?> saveNewRule() {
        return ResponseEntity.ok().build();
    }
}
