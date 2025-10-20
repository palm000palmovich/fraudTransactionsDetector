package com.example.AdminApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedResponse {
    private int statusCode;
    private Map<String, Object> body;
    private Map<String, String> headers;
}
