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

    @Bean
    public NewTopic newItemsTopic() {
        return TopicBuilder.name(inputTopic)
                .partitions(3)
                .replicas(2)
                .build();
    }

}
