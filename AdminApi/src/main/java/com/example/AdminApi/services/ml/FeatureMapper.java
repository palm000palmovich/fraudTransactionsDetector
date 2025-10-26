package com.example.AdminApi.services.ml;

import org.springframework.stereotype.Component;

/**
 * Маппинг строковых значений в числовые коды для ML модели.
 *
 * Важно: порядок и значения должны совпадать с тренировочными данными!
 *
 * TODO: Обновить когда будет версия модели с lat/lon вместо fixed locations
 */
@Component
public class FeatureMapper {

    /**
     * Маппинг типа транзакции (из description).
     *
     * Значения:
     * 0 = deposit
     * 1 = payment
     * 2 = transfer
     * 3 = withdrawal (default)
     *
     * @param description Описание транзакции
     * @return Код типа транзакции (0-3)
     */
    public int mapTransactionType(String description) {
        if (description == null) return 3; // default: withdrawal
        return switch (description.toLowerCase()) {
            case "deposit" -> 0;
            case "payment" -> 1;
            case "transfer" -> 2;
            case "withdrawal" -> 3;
            default -> 3;
        };
    }

    /**
     * Маппинг локации (из geo).
     *
     * Значения:
     * 0 = Berlin
     * 1 = Dubai
     * 2 = London
     * 3 = New York
     * 4 = Singapore
     * 5 = Sydney
     * 6 = Tokyo (default)
     * 7 = Toronto
     *
     * TODO: В следующей версии модели заменить на lat/lon
     *
     * @param geo Географическое местоположение
     * @return Код локации (0-7)
     */
    public int mapLocation(String geo) {
        if (geo == null) return 6; // default: Tokyo
        return switch (geo) {
            case "Berlin" -> 0;
            case "Dubai" -> 1;
            case "London" -> 2;
            case "New York" -> 3;
            case "Singapore" -> 4;
            case "Sydney" -> 5;
            case "Tokyo" -> 6;
            case "Toronto" -> 7;
            default -> 6;
        };
    }

    /**
     * Маппинг устройства (ВРЕМЕННАЯ ЗАГЛУШКА).
     *
     * Значения:
     * 0 = atm
     * 1 = mobile
     * 2 = pos
     * 3 = web (default)
     *
     * TODO: Убрать когда будет новая версия модели без device_used
     *
     * @param device Устройство (сейчас не используется)
     * @return Всегда 3 (web)
     */
    public int mapDeviceUsed(String device) {
        return 3; // web (дефолт) - заглушка
    }

    /**
     * Маппинг канала оплаты (из channel).
     *
     * Значения:
     * 0 = ACH
     * 1 = UPI
     * 2 = card (default)
     * 3 = wire_transfer
     *
     * @param channel Канал оплаты
     * @return Код канала (0-3)
     */
    public int mapPaymentChannel(String channel) {
        if (channel == null) return 2; // default: card
        return switch (channel.toLowerCase()) {
            case "ach" -> 0;
            case "upi" -> 1;
            case "card" -> 2;
            case "wire_transfer" -> 3;
            default -> 2;
        };
    }

    /**
     * Маппинг категории мерчанта (ВРЕМЕННАЯ ЗАГЛУШКА).
     *
     * Значения:
     * 0 = entertainment
     * 1 = grocery
     * 2 = online
     * 3 = other (default)
     * 4 = restaurant
     * 5 = retail
     * 6 = travel
     * 7 = utilities
     *
     * TODO: Убрать когда будет новая версия модели без merchant_category
     *
     * @param category Категория (сейчас не используется)
     * @return Всегда 3 (other)
     */
    public int mapMerchantCategory(String category) {
        return 3; // other (дефолт) - заглушка
    }
}
