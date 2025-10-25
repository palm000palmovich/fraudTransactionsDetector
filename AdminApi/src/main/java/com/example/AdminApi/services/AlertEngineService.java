package com.example.AdminApi.services;

import com.example.AdminApi.dto.MessageAlertDto;
import com.example.AdminApi.repositories.AlertMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlertEngineService {
    private final AlertMessageRepository alertMessageRepository;

    public void sendAlertToUser(MessageAlertDto messageAlertDto) {

        //TODO здесь будет отправка
    }
}
