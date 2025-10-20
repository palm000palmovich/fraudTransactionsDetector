package com.example.AdminApi.services;

import com.example.AdminApi.component.RedisCacheUtils;
import com.example.AdminApi.dto.CachedResponse;
import com.example.AdminApi.dto.IdempotencyCache;
import com.example.AdminApi.dto.MakeTransactionDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final ObjectMapper objectMapper;
    private final RedisCacheUtils redisCacheUtils;

    @Value("${spring.cache.redis.idempotency-key-ttl}")
    private int IDEMPOTENCY_KEY_TTL;

    private final String IDEMPOTENCY_KEY_PREFIX = "idempotency::";

    public Optional<IdempotencyCache> getCachedResponse(String idempotencyKey) {
        try {
            String cacheKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
            IdempotencyCache cached = redisCacheUtils.getValue(cacheKey, IdempotencyCache.class);

            if (cached != null) {
                log.info("Found cached response for idempotency key: {}", idempotencyKey);
                return Optional.of(cached);
            } else {
                log.error("Cached object is null.");
            }
        } catch (Exception e) {
            log.warn("Error reading from idempotency cache for key: {}", idempotencyKey, e);
        }

        return Optional.empty();
    }

    public void cacheResponse(String idempotencyKey, MakeTransactionDto request, ResponseEntity<?> response) {
        try {
            String cacheKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

            CachedResponse cachedResponse1 = CachedResponse.builder()
                    .statusCode(response.getStatusCode().value())
                    .body((Map<String, Object>) response.getBody())
                    .headers(response.getHeaders().toSingleValueMap())
                    .build();

            IdempotencyCache cacheEntry = IdempotencyCache.builder()
                    .idempotencyKey(idempotencyKey)
                    .requestHash(generateRequestHash(request))
                    .cachedResponse(cachedResponse1)
                    .createdAt(Instant.now())
                    .build();

            redisCacheUtils.putValue(cacheKey, cacheEntry, IDEMPOTENCY_KEY_TTL);
            log.info("Cached response for idempotency key: {}", idempotencyKey);

        } catch (Exception e) {
            log.warn("Error caching idempotency response for key: {}", idempotencyKey, e);
        }
    }

    private String generateRequestHash(MakeTransactionDto request) {
        try {
            String requestString = objectMapper.writeValueAsString(request);
            return DigestUtils.md5DigestAsHex(requestString.getBytes());
        } catch (Exception e) {
            return "unknown";
        }
    }

    public ResponseEntity<?> convertToResponse(CachedResponse cachedResponse) {
        return ResponseEntity.status(cachedResponse.getStatusCode())
                .headers(httpHeaders -> {
                    if (cachedResponse.getHeaders() != null) {
                        cachedResponse.getHeaders().forEach(httpHeaders::set);
                    }
                })
                .body(cachedResponse.getBody());
    }

}
