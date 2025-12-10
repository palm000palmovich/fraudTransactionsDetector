package com.example.AdminApi.configuration;

import com.example.AdminApi.component.AlertWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AlertWebSocketHandler alertWebSocketHandler;

    public WebSocketConfig(AlertWebSocketHandler alertWebSocketHandler) {
        this.alertWebSocketHandler = alertWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertWebSocketHandler, "/api/ws/alerts")
                .setAllowedOrigins("*");
    }
}
