package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyCache implements Serializable {
    private String idempotencyKey;
    private String requestHash;
    private CachedResponse cachedResponse;
    private Instant createdAt;
}
