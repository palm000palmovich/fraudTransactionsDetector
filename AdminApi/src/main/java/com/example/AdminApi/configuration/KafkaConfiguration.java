package com.example.AdminApi.configuration;

import com.example.AdminApi.dto.InputTransactionDto;
import com.example.AdminApi.dto.MessageAlertDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaConfiguration {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    //Producer для InputTransactionDto
    @Bean
    public ProducerFactory<String, InputTransactionDto> acceptEventProducerFactory() {
        log.warn("Configuring Kafka ProducerFactory with bootstrap servers: {}", bootstrapServers);

        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, InputTransactionDto> acceptEventKafkaTemplate() {
        return new KafkaTemplate<>(acceptEventProducerFactory());
    }

    // Producer для Object (DLQ топик - любые сообщения)
    @Bean
    public ProducerFactory<String, Object> dlqProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> dlqKafkaTemplate() {
        return new KafkaTemplate<>(dlqProducerFactory());
    }


    // Producer для MessageAlertDto
    @Bean
    public ProducerFactory<String, MessageAlertDto> alertProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Ленивая семантика для алертов - скорость важнее надежности
        configProps.put(ProducerConfig.ACKS_CONFIG, "1"); // Ждем подтверждения только от лидера
        configProps.put(ProducerConfig.RETRIES_CONFIG, 1); // Всего 1 попытка повтора
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false); // Без идемпотентности
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // Параллельные запросы
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10); // Накопление сообщений 10ms для батчинга
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // Размер батча 16KB
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000); // Таймаут 30 секунд
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 15000); // Таймаут запроса 15 секунд

        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, MessageAlertDto> alertKafkaTemplate() {
        return new KafkaTemplate<>(alertProducerFactory());
    }

    //Consumer для MessageAlertDto
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageAlertDto> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, MessageAlertDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, MessageAlertDto> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "alert-messages");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, MessageAlertDto.class);

        return new DefaultKafkaConsumerFactory<>(props);
    }

}
