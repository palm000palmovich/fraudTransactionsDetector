// Webhook Handler for frontend
class WebhookHandler {
    constructor() {
        this.history = [];
        this.init();
    }

    init() {
        // Эндпоинт для приема вебхуков от бэкенда
        this.setupWebhookEndpoint();
    }

    setupWebhookEndpoint() {
        // В реальном приложении здесь был бы серверный код
        // Для статического фронтенда используем другой подход
        console.log('Webhook handler initialized');
    }

    // Симуляция получения вебхука (для тестирования)
    simulateWebhook(alertData) {
        console.log('Simulating webhook:', alertData);
        if (typeof WebhookAPI !== 'undefined') {
            WebhookAPI.handleIncomingWebhook(alertData);
        }
    }

    // Тестовый метод для проверки
    testWebhook() {
        const testAlert = {
            ruleID: 1,
            ruleName: "Large Amount Transaction",
            reason: "Transaction amount exceeds threshold",
            sendingTime: new Date().toISOString(),
            transactionCorrelationId: "test-" + Date.now(),
            alertType: "FRAUD_ALERT",
            source: "FRAUD_DETECTION_SERVICE",
            timestamp: new Date().toISOString()
        };
        this.simulateWebhook(testAlert);
    }
}

// Глобальный экземпляр
const webhookHandler = new WebhookHandler();