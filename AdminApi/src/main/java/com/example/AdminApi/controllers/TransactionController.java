package com.example.AdminApi.controllers;

import com.example.AdminApi.services.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(path = "/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;


    @PostMapping(path = "/make-a-transaction/{sourceId}/{destinationId}/{amount}/{currency}")
    public ResponseEntity<?> makeATransaction(@PathVariable("sourceId") Long sourceId,
                                              @PathVariable("destinationId") Long destinationId,
                                              @PathVariable("amount") double amount,
                                              @PathVariable("currency") String currency) {
        return ResponseEntity.ok().build();
    }
}
