# 🔄 План слияния проектов - Детальный анализ и пошаговое руководство

> **Дата:** 2025-01-26
> **Цель:** Объединить ML-функциональность (ваш проект) с системой уведомлений (проект товарища)

---

## 📊 Критическое открытие

**Проект товарища находится ВНУТРИ вашего проекта!**

```
repozitorij-dlya-raboty-7408-new/              ← Ваш проект (с ML)
├── AdminApi/                                   ← Основной модуль
│   └── services/AlertEngineService.java        ← ЗАГЛУШКА (TODO)
│
└── repozitorij-dlya-raboty-7408/              ← Вложенный репозиторий товарища
    └── FraudWatchApi/                         ← Модуль с уведомлениями
        └── services/AlertEngineService.java    ← ПОЛНАЯ реализация
```

**Это означает:**
- Не нужно клонировать внешний репозиторий
- Все файлы уже доступны локально
- Задача: консолидация модулей, а не слияние репозиториев

---

## 📦 Инвентаризация: что есть в каждом проекте

### ✅ Ваш проект (AdminApi) - ИМЕЕТ:

#### ML Инфраструктура (3 сервиса + модель)
1. `services/ml/OnnxModelService.java` - ONNX inference
2. `services/ml/FeatureBuilder.java` - построение признаков
3. `services/ml/FeatureMapper.java` - маппинг признаков
4. `resources/ml/fraud_model_production_15f.onnx` (544 KB)

#### ML Миграции
- `V8__add_ml_fraud_detection_rule.sql`
- `V9__add_rule_metadata_column.sql`

#### ML Конфигурация
```properties
ml.model.path=classpath:ml/fraud_model_production_15f.onnx
ml.model.version=production-15f
ml.model.default-threshold=0.5
ml.features.deviation-window-days=30
ml.features.velocity-window-minutes=10
```

#### ML Dependency
```xml
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>1.19.2</version>
</dependency>
```

#### Уведомления (частично)
- `services/AlertEngineService.java` - **ЗАГЛУШКА** с TODO
- `consumer/AlertMessagesConsumer.java` - есть consumer, но пустая обработка
- `models/AlertMessageEntity.java` - модель для БД
- `V7__create_alerted_message_transactions.sql` - таблица alerts

---

### ✅ Проект товарища (FraudWatchApi) - ИМЕЕТ:

#### Telegram Integration (2 компонента + конфиг)
1. **component/AlertTelegramBot.java** (212 строк)
   - Extends TelegramLongPollingBot
   - Команды: `/start`, `/stop`, `/status`
   - Регистрация пользователей в БД
   - Рассылка алертов всем активным чатам
   - Tracking последнего уведомления

2. **component/AlertWebSocketHandler.java** (87 строк)
   - Управление WebSocket соединениями
   - Broadcast алертов в real-time
   - Thread-safe сессии
   - Подсчет активных подключений

3. **configuration/TelegramBotConfig.java** (37 строк)
   - Регистрация бота в Spring
   - TelegramBotsApi + DefaultBotSession

#### WebSocket Infrastructure (1 конфиг)
4. **configuration/WebSocketConfig.java** (23 строк)
   - Регистрация `/api/ws/alerts` endpoint
   - CORS настройки для WebSocket
   - Включение WebSocket support

#### Webhook Service (2 файла)
5. **services/WebhookService.java** (124 строки)
   - HTTP webhook отправка
   - Retry логика (до 3 попыток)
   - WebSocket broadcasting
   - Configurable retry count/delay
   - Error handling с логированием

6. **configuration/WebhookConfig.java** (35 строк)
   - RestTemplate для HTTP вызовов
   - Timeouts: 5s connect, 10s read
   - Error handler configuration

#### Full Alert Engine (1 сервис - ЗАМЕНА)
7. **services/AlertEngineService.java** (153 строки) **← ГЛАВНЫЙ ФАЙЛ**
   - Полная реализация вместо заглушки
   - Сохранение в БД (AlertMessageEntity)
   - Отправка в Telegram (через AlertTelegramBot)
   - Отправка Webhook (через WebhookService)
   - Отправка WebSocket (через WebhookService)
   - **@Async** для неблокирующей отправки
   - Error handling для каждого канала

#### Kafka Consumer (1 файл - ЗАМЕНА)
8. **consumer/AlertMessagesConsumer.java** (29 строк)
   - Слушает `alert-topic`
   - Принимает `MessageAlertDto`
   - Делегирует в `AlertEngineService.sendAlertToUser()`
   - Error handling

#### Database Entities (1 модель)
9. **models/ChatEntity.java** (103 строки)
   - Telegram chat persistence
   - Поля: chatId, firstName, lastName, username, active
   - registeredAt, lastNotifiedAt
   - Таблица: `chats`

10. **repositories/ChatRepository.java** (12 строк)
    - `findByActiveTrue()`
    - `findByChatId(String)`

#### DTOs (1 новый)
11. **dto/WebhookAlertDto.java** (94 строки)
    - Extends MessageAlertDto
    - Дополнительные поля: alertType, source, timestamp, status, metadata
    - Для HTTP webhook payloads

#### Frontend (2 файла)
12. **static/js/webhook-handler.js** (новый, 45 строк)
    - Клиентский тестер вебхуков
    - Симуляция webhook запросов

13. **static/js/dashboard.js** (изменения, ~50 строк)
    - WebSocket клиент (строки 1582-1631)
    - Auto-reconnect при дисконнекте (5s delay)
    - Real-time alert display
    - Status indicator updates

#### Database Migration (1 файл)
14. **V8__Create_chats_table.sql**
    - Создание таблицы `chats`
    - Индексы на active, chat_id
    - **⚠️ КОНФЛИКТ:** У вас V8 = ML rule, у товарища V8 = chats

#### Dependencies (3 новых)
```xml
<!-- Telegram Bot -->
<dependency>
    <groupId>org.telegram</groupId>
    <artifactId>telegrambots</artifactId>
    <version>6.8.0</version>
</dependency>

<!-- WebFlux для HTTP webhooks -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- WebSocket -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

#### Configuration Properties (9 новых)
```properties
# Webhook
app.webhook.enabled=true
app.webhook.url=http://localhost:8081/api/webhooks/test
app.webhook.timeout=5000
app.webhook.retry.count=3
app.webhook.retry.delay=1000

# Telegram
app.telegram.bot.token=8479535722:AAHU4IhLGA_DUv8tdVnZMSE385w_lNJMdbA

# Async
spring.task.execution.pool.core-size=5
spring.task.execution.pool.max-size=10
spring.task.execution.pool.queue-capacity=25

# CORS
app.frontend.url=http://localhost:8081
```

---

## ⚠️ Конфликты и их разрешение

### Конфликт 1: AlertEngineService.java
- **Ваш файл:** Заглушка с `//TODO здесь будет отправка`
- **Файл товарища:** Полная реализация с 3 каналами
- **Решение:** **ЗАМЕНИТЬ** ваш файл на файл товарища

### Конфликт 2: AlertMessagesConsumer.java
- **Ваш файл:** Есть consumer, но пустая обработка
- **Файл товарища:** Полная обработка с делегированием в AlertEngineService
- **Решение:** **ЗАМЕНИТЬ** ваш файл на файл товарища

### Конфликт 3: Migration V8
- **Ваш V8:** `V8__add_ml_fraud_detection_rule.sql` (ML)
- **V8 товарища:** `V8__Create_chats_table.sql` (Telegram)
- **Решение:** **ПЕРЕИМЕНОВАТЬ** V8 товарища → `V10__Create_chats_table.sql`

### Конфликт 4: pom.xml
- **Ваши зависимости:** ONNX Runtime
- **Зависимости товарища:** Telegram + WebFlux + WebSocket
- **Решение:** **ОБЪЕДИНИТЬ** все зависимости (нет реального конфликта)

### Конфликт 5: application.properties
- **Ваши свойства:** ML конфигурация
- **Свойства товарища:** Webhook + Telegram + Async
- **Решение:** **ОБЪЕДИНИТЬ** все свойства (нет реального конфликта)

### Конфликт 6: dashboard.js
- **Ваш файл:** Текущая версия без WebSocket
- **Файл товарища:** Добавлен WebSocket клиент (строки 1582-1631)
- **Решение:** **ДОБАВИТЬ** WebSocket код в конец файла

---

## 🎯 Пошаговый план слияния

### 📋 ЭТАП 1: Подготовка (15 минут)

#### Шаг 1.1: Создать резервную копию
```bash
cd C:\Users\maliu\IdeaProjects
cp -r repozitorij-dlya-raboty-7408-new repozitorij-dlya-raboty-7408-new-backup
```

#### Шаг 1.2: Создать feature ветку
```bash
cd repozitorij-dlya-raboty-7408-new
git checkout -b feature/merge-notifications
git add .
git commit -m "Checkpoint before notifications merge"
```

#### Шаг 1.3: Проверить пути к FraudWatchApi
```bash
# Проверить что FraudWatchApi существует
ls -la repozitorij-dlya-raboty-7408/FraudWatchApi/
```

---

### 📋 ЭТАП 2: Копирование Java файлов (30 минут)

#### Шаг 2.1: Создать недостающие директории
```bash
cd AdminApi/src/main/java/com/example/AdminApi
mkdir -p component
# component уже должна существовать, если нет - создастся
```

#### Шаг 2.2: Скопировать компоненты Telegram & WebSocket
```bash
# Из корня repozitorij-dlya-raboty-7408-new
cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/java/com/example/AdminApi/component/AlertTelegramBot.java \
   AdminApi/src/main/java/com/example/AdminApi/component/

cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/java/com/example/AdminApi/component/AlertWebSocketHandler.java \
   AdminApi/src/main/java/com/example/AdminApi/component/
```

#### Шаг 2.3: Скопировать конфигурации
```bash
cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/java/com/example/AdminApi/configuration/TelegramBotConfig.java \
   AdminApi/src/main/java/com/example/AdminApi/configuration/

cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/java/com/example/AdminApi/configuration/WebSocketConfig.java \
   AdminApi/src/main/java/com/example/AdminApi/configuration/

cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/java/com/example/AdminApi/configuration/WebhookConfig.java \
   AdminApi/src/main/java/com/example/AdminApi/configuration/
```

#### Шаг 2.4: ЗАМЕНИТЬ AlertEngineService (ВАЖНО!)
```bash
# Сначала сохраните старый для справки
cp AdminApi/src/main/java/com/example/AdminApi/services/AlertEngineService.java \
   AdminApi/src/main/java/com/example/AdminApi/services/AlertEngineService.java.OLD

# Замените на новый
cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/java/com/example/AdminApi/services/AlertEngineService.java \
   AdminApi/src/main/java/com/example/AdminApi/services/
```

#### Шаг 2.5: Скопировать WebhookService
```bash
cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/java/com/example/AdminApi/services/WebhookService.java \
   AdminApi/src/main/java/com/example/AdminApi/services/
```

#### Шаг 2.6: ЗАМЕНИТЬ AlertMessagesConsumer
```bash
# Сохранить старый
cp AdminApi/src/main/java/com/example/AdminApi/consumer/AlertMessagesConsumer.java \
   AdminApi/src/main/java/com/example/AdminApi/consumer/AlertMessagesConsumer.java.OLD

# Заменить на новый
cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/java/com/example/AdminApi/consumer/AlertMessagesConsumer.java \
   AdminApi/src/main/java/com/example/AdminApi/consumer/
```

#### Шаг 2.7: Скопировать ChatEntity
```bash
cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/java/com/example/AdminApi/models/ChatEntity.java \
   AdminApi/src/main/java/com/example/AdminApi/models/
```

#### Шаг 2.8: Скопировать ChatRepository
```bash
cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/java/com/example/AdminApi/repositories/ChatRepository.java \
   AdminApi/src/main/java/com/example/AdminApi/repositories/
```

#### Шаг 2.9: Скопировать WebhookAlertDto
```bash
cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/java/com/example/AdminApi/dto/WebhookAlertDto.java \
   AdminApi/src/main/java/com/example/AdminApi/dto/
```

---

### 📋 ЭТАП 3: Обновление зависимостей (15 минут)

#### Шаг 3.1: Открыть AdminApi/pom.xml

#### Шаг 3.2: Добавить в секцию <dependencies> ПЕРЕД </dependencies>
```xml
        <!-- Telegram Bot -->
        <dependency>
            <groupId>org.telegram</groupId>
            <artifactId>telegrambots</artifactId>
            <version>6.8.0</version>
        </dependency>

        <!-- HTTP client for webhooks -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- WebSocket -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
```

#### Шаг 3.3: Сохранить и проверить
```bash
cd AdminApi
mvn dependency:tree | grep -E "telegram|webflux|websocket"
```

---

### 📋 ЭТАП 4: Обновление конфигурации (15 минут)

#### Шаг 4.1: Открыть AdminApi/src/main/resources/application.properties

#### Шаг 4.2: Добавить в конец файла (после ML конфигурации)
```properties
# ============================================
# NOTIFICATION SYSTEM CONFIGURATION
# ============================================

# Webhook Configuration
app.webhook.enabled=true
app.webhook.url=http://localhost:8081/api/webhooks/test
app.webhook.timeout=5000
app.webhook.retry.count=3
app.webhook.retry.delay=1000

# Telegram Bot Configuration
# TODO: Заменить на ваш токен бота
app.telegram.bot.token=YOUR_BOT_TOKEN_HERE

# Async Task Execution
spring.task.execution.pool.core-size=5
spring.task.execution.pool.max-size=10
spring.task.execution.pool.queue-capacity=25

# Frontend URL for CORS
app.frontend.url=http://localhost:8081
```

#### Шаг 4.3: Получить Telegram Bot Token
1. Открыть Telegram
2. Найти [@BotFather](https://t.me/botfather)
3. Отправить `/newbot` или `/token` для существующего бота
4. Скопировать токен
5. Заменить `YOUR_BOT_TOKEN_HERE` на реальный токен

---

### 📋 ЭТАП 5: Database Migration (10 минут)

#### Шаг 5.1: Создать новую миграцию V10
```bash
cd AdminApi/src/main/resources/db/migration
```

#### Шаг 5.2: Скопировать V8 товарища → V10
```bash
cp ../../../../../repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/resources/db/migration/V8__Create_chats_table.sql \
   V10__Create_chats_table.sql
```

#### Шаг 5.3: Проверить последовательность миграций
```bash
ls -la db/migration/
# Должно быть:
# V1, V2, V3, V4, V5, V6, V7, V8 (ML), V9 (metadata), V10 (chats) ✅
```

---

### 📋 ЭТАП 6: Frontend обновления (1 час)

#### Шаг 6.1: Скопировать webhook-handler.js
```bash
cp repozitorij-dlya-raboty-7408/FraudWatchApi/src/main/resources/static/js/webhook-handler.js \
   AdminApi/src/main/resources/static/js/
```

#### Шаг 6.2: Обновить dashboard.js - WebSocket код

**Открыть:** `AdminApi/src/main/resources/static/js/dashboard.js`

**Добавить В КОНЕЦ файла (перед последней закрывающей скобкой):**

```javascript
// ============================================
// WEBSOCKET INTEGRATION FOR REAL-TIME ALERTS
// ============================================

let ws = null;
let wsReconnectTimer = null;

// Initialize WebSocket connection
function initWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/api/ws/alerts`;

    console.log('Connecting to WebSocket:', wsUrl);

    ws = new WebSocket(wsUrl);

    ws.onopen = function() {
        console.log('WebSocket connected');
        updateConnectionStatus(true);
        clearReconnectTimer();
    };

    ws.onmessage = function(event) {
        console.log('WebSocket message received:', event.data);
        try {
            const alert = JSON.parse(event.data);
            handleRealtimeAlert(alert);
        } catch (e) {
            console.error('Failed to parse WebSocket message:', e);
        }
    };

    ws.onerror = function(error) {
        console.error('WebSocket error:', error);
        updateConnectionStatus(false);
    };

    ws.onclose = function() {
        console.log('WebSocket disconnected');
        updateConnectionStatus(false);
        scheduleReconnect();
    };
}

// Handle real-time alert from WebSocket
function handleRealtimeAlert(alert) {
    console.log('Real-time alert received:', alert);

    // Show notification
    if (Notification.permission === 'granted') {
        new Notification('🚨 Подозрительная транзакция', {
            body: `${alert.ruleName}: ${alert.reason}`,
            icon: '/favicon.ico'
        });
    }

    // Reload data if on relevant tab
    const activeTab = document.querySelector('.tab-content.active');
    if (activeTab) {
        const tabId = activeTab.id;
        if (tabId === 'transactions') {
            loadTransactions();
        } else if (tabId === 'notifications') {
            loadNotifications();
        }
    }
}

// Update connection status indicator
function updateConnectionStatus(connected) {
    const indicator = document.getElementById('wsStatus');
    if (indicator) {
        indicator.textContent = connected ? '🟢 Connected' : '🔴 Disconnected';
        indicator.className = connected ? 'ws-status connected' : 'ws-status disconnected';
    }
}

// Schedule WebSocket reconnection
function scheduleReconnect() {
    clearReconnectTimer();
    wsReconnectTimer = setTimeout(function() {
        console.log('Attempting to reconnect WebSocket...');
        initWebSocket();
    }, 5000); // 5 seconds
}

// Clear reconnect timer
function clearReconnectTimer() {
    if (wsReconnectTimer) {
        clearTimeout(wsReconnectTimer);
        wsReconnectTimer = null;
    }
}

// Request notification permissions on load
if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission();
}

// Initialize WebSocket when authenticated
if (Auth.isAuthenticated()) {
    initWebSocket();
}
```

#### Шаг 6.3: Добавить WebSocket status indicator в HTML

**Открыть:** `AdminApi/src/main/resources/static/dashboard.html`

**Найти секцию с header и добавить статус индикатор:**

```html
<header class="header">
    <div class="header-content">
        <div class="header-left">
            <h1>Финансовый радар</h1>
            <p class="user-info">
                Пользователь: <span id="userEmail">admin@example.com</span> |
                Роль: <span id="userRole" style="font-weight: bold;">ADMIN</span> |
                <span id="wsStatus" class="ws-status">🔴 Disconnected</span>
            </p>
        </div>
        <!-- ... rest of header ... -->
```

#### Шаг 6.4: Добавить CSS для WebSocket статуса

**Открыть:** `AdminApi/src/main/resources/static/css/styles.css`

**Добавить в конец:**

```css
/* WebSocket status indicator */
.ws-status {
    font-size: 0.875rem;
    padding: 0.25rem 0.5rem;
    border-radius: 0.25rem;
    font-weight: 600;
}

.ws-status.connected {
    color: #059669;
    background-color: #d1fae5;
}

.ws-status.disconnected {
    color: #dc2626;
    background-color: #fee2e2;
}
```

---

### 📋 ЭТАП 7: Сборка и тестирование (2 часа)

#### Шаг 7.1: Сборка проекта
```bash
cd AdminApi
mvn clean install -DskipTests
```

**Ожидаемый результат:** BUILD SUCCESS

**Если ошибки:**
- Проверить все импорты в новых файлах
- Проверить что все зависимости добавлены
- Проверить package names

#### Шаг 7.2: Запуск Flyway миграций
```bash
mvn flyway:info
# Проверить что V10 появилась в списке

mvn flyway:migrate
# Применить миграцию V10
```

#### Шаг 7.3: Запуск приложения
```bash
mvn spring-boot:run
```

**Проверить в логах:**
- ✅ "Telegram bot registered successfully"
- ✅ "WebSocket handler registered at /api/ws/alerts"
- ✅ "AlertEngineService initialized"
- ✅ "ML Model loaded: production-15f"

#### Шаг 7.4: Тест Telegram бота

**Terminal:**
```bash
# Открыть Telegram
# Найти вашего бота по username
# Отправить команду: /start
```

**Ожидаемый результат:**
```
Добро пожаловать в систему алертов Финансовый Радар!

Вы успешно зарегистрированы для получения уведомлений о подозрительных транзакциях.

Доступные команды:
/status - проверить статус
/stop - отключить уведомления
```

**Проверить в БД:**
```sql
SELECT * FROM chats;
-- Должна появиться запись с вашим chat_id
```

#### Шаг 7.5: Тест WebSocket соединения

**Браузер:**
1. Открыть http://localhost:8081/dashboard.html
2. Войти как admin
3. Проверить статус в header: должно быть "🟢 Connected"
4. Открыть Developer Tools → Network → WS
5. Должно быть активное соединение к `/api/ws/alerts`

#### Шаг 7.6: End-to-End тест (создать алерт)

**Создать тестовую транзакцию:**
```bash
curl -X POST http://localhost:8081/api/transactions \
  -H "Content-Type: application/json" \
  -H "Idempotency-key: test-merge-$(date +%s)" \
  -u admin:admin123 \
  -d '{
    "from": "test-account-999",
    "to": "receiver-123",
    "amount": 999999,
    "type": "transfer",
    "timestamp": "2025-01-26T15:00:00",
    "geo": "Moscow",
    "channel": "online"
  }'
```

**Ожидаемые результаты:**

1. **В Telegram:**
   - Получите сообщение с алертом
   - Формат: "🚨 Подозрительная транзакция: [детали]"

2. **В браузере (WebSocket):**
   - Должно появиться уведомление браузера
   - Таблица транзакций обновится автоматически

3. **В БД:**
```sql
SELECT * FROM alert_messages ORDER BY created_at DESC LIMIT 1;
-- Должна быть новая запись

SELECT * FROM transactions WHERE source_id = 'test-account-999';
-- status должен быть 'ALERTED'
```

4. **В логах приложения:**
```
INFO  AlertEngineService - Sending alert for transaction: [correlation-id]
INFO  AlertEngineService - Alert saved to database
INFO  AlertTelegramBot - Broadcasting alert to 1 active chats
INFO  WebhookService - Sending webhook to: http://localhost:8081/api/webhooks/test
INFO  AlertWebSocketHandler - Broadcasting alert to 1 connected clients
```

---

### 📋 ЭТАП 8: Cleanup и финализация (30 минут)

#### Шаг 8.1: Удалить вложенный репозиторий товарища
```bash
cd C:\Users\maliu\IdeaProjects\repozitorij-dlya-raboty-7408-new

# Удалить FraudWatchApi (больше не нужен)
rm -rf repozitorij-dlya-raboty-7408/

# Проверить что всё работает после удаления
mvn clean install
mvn spring-boot:run
```

#### Шаг 8.2: Удалить backup файлы
```bash
cd AdminApi/src/main/java/com/example/AdminApi

# Удалить старые версии
rm services/AlertEngineService.java.OLD
rm consumer/AlertMessagesConsumer.java.OLD
```

#### Шаг 8.3: Обновить .gitignore
```bash
# Добавить в .gitignore если ещё нет:
echo "repozitorij-dlya-raboty-7408/" >> .gitignore
```

#### Шаг 8.4: Закоммитить изменения
```bash
git add .
git status
# Проверить что добавлены:
# - новые Java файлы (10 файлов)
# - изменения в pom.xml
# - изменения в application.properties
# - новая миграция V10
# - изменения во frontend

git commit -m "feat: integrate notification system (Telegram + WebSocket + Webhook)

- Add Telegram bot integration (AlertTelegramBot, TelegramBotConfig)
- Add WebSocket real-time alerts (AlertWebSocketHandler, WebSocketConfig)
- Add Webhook service with retry logic (WebhookService, WebhookConfig)
- Replace AlertEngineService stub with full implementation
- Replace AlertMessagesConsumer with functional version
- Add ChatEntity and ChatRepository for Telegram user management
- Add V10 migration for chats table
- Add WebSocket client in dashboard.js
- Add webhook-handler.js for testing
- Update pom.xml with telegrambots, webflux, websocket dependencies
- Update application.properties with notification configs

Co-authored-by: [Имя товарища] <email@example.com>"
```

#### Шаг 8.5: Merge в main branch
```bash
git checkout development_ilia
git merge feature/merge-notifications
git push origin development_ilia
```

---

## 📊 Чек-лист финальной проверки

### ✅ Backend
- [ ] Все Java файлы скомпилированы без ошибок
- [ ] Миграции применены успешно (V1-V10)
- [ ] Telegram бот регистрируется при старте
- [ ] WebSocket endpoint доступен на `/api/ws/alerts`
- [ ] AlertEngineService имеет 3 метода отправки (Telegram, Webhook, WebSocket)
- [ ] ML модель загружается корректно
- [ ] Нет конфликтов зависимостей в pom.xml

### ✅ Database
- [ ] Таблица `chats` создана (V10)
- [ ] Таблица `alert_messages` существует (V7)
- [ ] Индексы созданы на обеих таблицах

### ✅ Configuration
- [ ] Telegram bot token настроен
- [ ] Webhook URL настроен
- [ ] Async pool настроен
- [ ] ML конфигурация не затронута

### ✅ Frontend
- [ ] WebSocket статус индикатор виден в header
- [ ] WebSocket подключается при загрузке
- [ ] Real-time уведомления работают
- [ ] webhook-handler.js доступен

### ✅ End-to-End
- [ ] Создание транзакции → ML оценка → Rule trigger → Alert
- [ ] Alert сохраняется в БД
- [ ] Telegram уведомление получено
- [ ] WebSocket уведомление получено
- [ ] Webhook запрос отправлен

---

## 🚨 Troubleshooting - Частые проблемы

### Проблема 1: Telegram бот не запускается
**Симптомы:**
```
ERROR TelegramBotConfig - Failed to register bot: unauthorized
```

**Решение:**
1. Проверить bot token в application.properties
2. Убедиться что токен не истёк
3. Проверить нет ли пробелов в токене

### Проблема 2: WebSocket не подключается
**Симптомы:**
- Статус "🔴 Disconnected" не меняется
- В console: "WebSocket connection failed"

**Решение:**
1. Проверить что WebSocketConfig загружен:
   ```bash
   grep "WebSocket" logs/spring.log
   ```
2. Проверить CORS настройки
3. Попробовать другой браузер

### Проблема 3: Flyway migration conflict
**Симптомы:**
```
ERROR Flyway - Found more than one migration with version 8
```

**Решение:**
1. Удалить V10__Create_chats_table.sql
2. Переименовать V8 товарища в V10
3. Запустить `mvn flyway:repair`
4. Запустить `mvn flyway:migrate`

### Проблема 4: Import errors после копирования файлов
**Симптомы:**
```
Cannot resolve symbol 'AlertWebSocketHandler'
```

**Решение:**
1. Refresh Maven project в IDE
2. Invalidate caches and restart (IntelliJ)
3. Проверить package name в скопированных файлах

### Проблема 5: Уведомления не отправляются
**Симптомы:**
- Транзакция создана
- Status = ALERTED
- Но Telegram/WebSocket молчат

**Решение:**
1. Проверить что AlertMessagesConsumer запущен:
   ```bash
   grep "AlertMessagesConsumer" logs/spring.log
   ```
2. Проверить Kafka topic:
   ```bash
   docker exec -it kafka-0 kafka-console-consumer \
     --bootstrap-server kafka-0:9092 \
     --topic alert-topic --from-beginning
   ```
3. Проверить логи AlertEngineService

---

## 📈 Метрики успешного слияния

После завершения слияния у вас должно быть:

### Файловая структура
```
AdminApi/
├── component/
│   ├── AlertTelegramBot.java         ← НОВЫЙ
│   ├── AlertWebSocketHandler.java    ← НОВЫЙ
│   └── ... (другие компоненты)
│
├── configuration/
│   ├── TelegramBotConfig.java        ← НОВЫЙ
│   ├── WebSocketConfig.java          ← НОВЫЙ
│   ├── WebhookConfig.java            ← НОВЫЙ
│   └── ... (другие конфиги)
│
├── consumer/
│   └── AlertMessagesConsumer.java    ← ЗАМЕНЁН
│
├── models/
│   ├── ChatEntity.java               ← НОВЫЙ
│   └── AlertMessageEntity.java       ← СУЩЕСТВУЕТ
│
├── repositories/
│   └── ChatRepository.java           ← НОВЫЙ
│
├── services/
│   ├── AlertEngineService.java       ← ЗАМЕНЁН
│   ├── WebhookService.java           ← НОВЫЙ
│   └── ml/
│       ├── OnnxModelService.java     ← СУЩЕСТВУЕТ
│       ├── FeatureBuilder.java       ← СУЩЕСТВУЕТ
│       └── FeatureMapper.java        ← СУЩЕСТВУЕТ
│
├── resources/
│   ├── db/migration/
│   │   ├── V8__add_ml_fraud_detection_rule.sql    ← СУЩЕСТВУЕТ
│   │   ├── V9__add_rule_metadata_column.sql       ← СУЩЕСТВУЕТ
│   │   └── V10__Create_chats_table.sql            ← НОВЫЙ
│   │
│   └── static/js/
│       ├── dashboard.js              ← ИЗМЕНЁН (+ WebSocket)
│       └── webhook-handler.js        ← НОВЫЙ
│
└── pom.xml                           ← ИЗМЕНЁН (+ 3 зависимости)
```

### Статистика
- **Всего файлов добавлено:** 12
- **Файлов заменено:** 2
- **Файлов изменено:** 4 (pom.xml, application.properties, dashboard.js, dashboard.html)
- **Новых зависимостей:** 3
- **Новых свойств конфигурации:** 9
- **Новых миграций:** 1

### Функциональность
- ✅ ML модель работает (было)
- ✅ Telegram бот работает (добавлено)
- ✅ WebSocket работает (добавлено)
- ✅ Webhook работает (добавлено)
- ✅ Real-time уведомления в браузере (добавлено)
- ✅ История алертов в БД (добавлено)

---

## 🎯 Итоговая оценка

### Сложность слияния: ⭐⭐⭐☆☆ (3/5)

**Почему не сложно:**
- Нет конфликтующих изменений в одних и тех же файлах
- Проекты работают в разных слоях (ваш = ML, товарища = уведомления)
- Зависимости не конфликтуют
- Только 1 реальный конфликт миграций

**Потенциальные риски:**
- Telegram bot token должен быть валидным
- WebSocket CORS может потребовать настройки
- Async pool может потребовать тюнинга

### Время выполнения: ~6 часов

| Этап | Время | Риск |
|------|-------|------|
| Подготовка | 15 мин | Низкий |
| Копирование файлов | 30 мин | Низкий |
| Обновление зависимостей | 15 мин | Средний |
| Обновление конфигурации | 15 мин | Низкий |
| Database migration | 10 мин | Средний |
| Frontend обновления | 1 час | Средний |
| Сборка и тестирование | 2 часа | Высокий |
| Cleanup | 30 мин | Низкий |
| **Buffer** | 1 час 45 мин | - |
| **Итого** | **6 часов** | |

---

## 📞 Следующие шаги после слияния

### 1. Production готовность
- [ ] Переместить Telegram bot token в environment variable
- [ ] Настроить CORS для production URLs
- [ ] Добавить rate limiting для WebSocket connections
- [ ] Настроить retry limits для webhooks

### 2. Мониторинг
- [ ] Добавить метрики для уведомлений (sent, failed, latency)
- [ ] Настроить alerting для ошибок Telegram бота
- [ ] Настроить alerting для WebSocket disconnects

### 3. Документация
- [ ] Обновить API_ENDPOINTS.md с WebSocket endpoint
- [ ] Добавить инструкцию по настройке Telegram бота в README
- [ ] Документировать webhook payload format

### 4. Тестирование
- [ ] Написать unit тесты для AlertEngineService
- [ ] Написать integration тесты для Telegram bot
- [ ] Написать e2e тесты для WebSocket

---

**Успехов в слиянии! 🚀**

*Если возникнут проблемы - обращайтесь с конкретными ошибками для быстрой помощи.*
