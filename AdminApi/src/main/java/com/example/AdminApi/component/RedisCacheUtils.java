package com.example.AdminApi.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheUtils {
    private final RedisTemplate<String, Object> redisTemplate;

    public void putValue(String key, Object value, long timeoutInSeconds) throws RuntimeException{
        redisTemplate.opsForValue().set(key, value, timeoutInSeconds, TimeUnit.SECONDS);
    }


    public <T> T getValue(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }

            return type.cast(value);
        } catch (ClassCastException e) {
            log.warn("Type cast error for key: {}. Expected: {}",
                    key, type.getSimpleName());
            return null;
        } catch (Exception e) {
            log.error("Error retrieving key: {}", key, e);
            return null;
        }
    }

    // Удаление данных из кэша
    public void deleteValue(String key) throws RuntimeException{
        redisTemplate.delete(key);
    }

    //Проверка наличия ключа в кеше
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
