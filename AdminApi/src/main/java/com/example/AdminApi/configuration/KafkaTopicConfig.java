package com.example.AdminApi.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Value("${app.kafka.topic.input-transactions}")
    private String inputTopic;

    @Value("${app.kafka.topic.input-transactions-dlq}")
    private String inputTransactionsDlq;

    @Bean
    public NewTopic newItemsTopic() {
        return TopicBuilder.name(inputTopic)
                .partitions(3)
                .replicas(2)
                .config("retention.ms", "86400000") // 1 день для хранения в основном топике
                .config("cleanup.policy", "delete")
                .config("retention.bytes", "1073741824") // 1GB максимум
                .build();
    }

    @Bean
    public NewTopic dlqTopic() {
        return TopicBuilder.name(inputTransactionsDlq)
                .partitions(1)
                .replicas(2)
                .config("retention.ms", "604800000") //7 дней для хранения в DLQ
                .config("cleanup.policy", "delete")
                .build();
    }

}
