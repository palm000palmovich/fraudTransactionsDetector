package com.example.AdminApi.controllers;

import com.example.AdminApi.dto.UserInfoDto;
import com.example.AdminApi.models.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for authentication-related endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    /**
     * GET /api/auth/me - Get current authenticated user information
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfoDto> getCurrentUser(@AuthenticationPrincipal User user) {
        log.info("🔐 [AUTH STEP 7] GET /api/auth/me - Request received");
        log.debug("Fetching current authenticated user info");

        if (user == null) {
            log.error("❌ [AUTH ERROR] No authenticated user found in SecurityContext");
            return ResponseEntity.status(401).build();
        }

        log.info("✅ [AUTH STEP 8] User retrieved from SecurityContext: username={}", user.getUsername());

        UserInfoDto userInfo = UserInfoDto.builder()
            .username(user.getUsername())
            .role(user.getRole().name())
            .enabled(user.isEnabled())
            .build();

        log.info("✅ [AUTH COMPLETE] Returning user info to frontend: username={}, role={}",
            userInfo.getUsername(), userInfo.getRole());
        log.debug("Full user info: {}", userInfo);

        return ResponseEntity.ok(userInfo);
    }
}
