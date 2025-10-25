package com.example.AdminApi.controllers;

import com.example.AdminApi.dto.AuditLogDto;
import com.example.AdminApi.dto.PagedResponse;
import com.example.AdminApi.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Slf4j
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Get audit logs with filters and pagination
     * Accessible to both ADMIN and VIEWER roles
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VIEWER')")
    public ResponseEntity<PagedResponse<AuditLogDto>> getAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "newest") String sort) {

        log.info("Fetching audit logs: username={}, action={}, ruleId={}, page={}, size={}",
                username, action, ruleId, page, size);

        PagedResponse<AuditLogDto> response = auditLogService.getLogs(
                username, action, ruleId, dateFrom, dateTo, page, size, sort
        );

        return ResponseEntity.ok(response);
    }
}
