package com.example.AdminApi.exceptionHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        log.error("Validation exception: {}", ex.getMessage());
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (existing, replacement) -> existing + ", " + replacement
                ));

        Map<String, Object> response = Map.of(
                "status", "VALIDATION_ERROR",
                "message", "Invalid request parameters",
                "errors", errors,
                "timestamp", LocalDateTime.now()
        );

        return ResponseEntity.status(400).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        log.error("Internal server error {}", ex);

        Map<String, Object> response = Map.of(
                "status", "INTERNAL_ERROR",
                "message", "Internal server error occurred",
                "timestamp", LocalDateTime.now()
        );

        return ResponseEntity.status(500).body(response);
    }
}
