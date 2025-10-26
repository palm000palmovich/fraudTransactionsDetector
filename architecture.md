# Архитектура системы "Финансовый радар"

## Обзор

**Финансовый радар** — система обнаружения мошенничества в режиме реального времени с модульной архитектурой, ML-моделью и веб-панелью управления.

---

## Архитектурная диаграмма

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           CLIENT LAYER                                  │
├─────────────────────────────────────────────────────────────────────────┤
│  External Systems          Admin Panel (Browser)                        │
│  (Banking Apps, APIs)      http://localhost:8081/dashboard.html         │
└──────────┬──────────────────────────┬───────────────────────────────────┘
           │                          │
           │ POST /api/transactions   │ GET/POST/PUT/DELETE /api/*
           │ (Idempotency-key)        │
           ▼                          ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        API GATEWAY / REST LAYER                         │
│                       Spring Boot Application                            │
│                          (Port 8081)                                     │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌──────────────┐  ┌────────────────┐            │
│  │ Transaction     │  │ Rule         │  │ Audit Log      │            │
│  │ Controller      │  │ Controller   │  │ Controller     │            │
│  └────────┬────────┘  └──────┬───────┘  └────────────────┘            │
│           │                   │                                         │
│           ▼                   ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │              SERVICE LAYER                                  │       │
│  │  ┌──────────────┐ ┌─────────────┐ ┌──────────────────┐   │       │
│  │  │ Idempotency  │ │ Transaction │ │ Rule Engine      │   │       │
│  │  │ Service      │ │ Service     │ │ Service          │   │       │
│  │  └──────────────┘ └──────┬──────┘ └─────────┬────────┘   │       │
│  │                           │                   │             │       │
│  │                           ▼                   ▼             │       │
│  │  ┌──────────────────────────────────────────────────────┐ │       │
│  │  │      Transaction Processing Service                  │ │       │
│  │  │  - Validates transactions                            │ │       │
│  │  │  - Applies fraud detection rules                     │ │       │
│  │  │  - Calculates ML features                            │ │       │
│  │  │  - Updates status (PENDING→PROCESSED/ALERTED)        │ │       │
│  │  └──────────────────────────────────────────────────────┘ │       │
│  └─────────────────────────────────────────────────────────────┘       │
└────┬────────────────┬──────────────────┬─────────────────┬─────────────┘
     │                │                  │                 │
     │                │                  │                 │
     ▼                ▼                  ▼                 ▼
┌─────────┐    ┌─────────────┐   ┌─────────────┐  ┌────────────┐
│  Redis  │    │   Kafka     │   │ PostgreSQL  │  │ Prometheus │
│  Cache  │    │  Cluster    │   │  Database   │  │  Metrics   │
└─────────┘    └─────────────┘   └─────────────┘  └────────────┘
  (6379)       (3 brokers)          (5433)           (9090)
                                                         │
     │                │                  │               │
     │                ▼                  │               ▼
     │         ┌─────────────┐           │        ┌──────────┐
     │         │  Consumers  │           │        │ Grafana  │
     │         ├─────────────┤           │        │Dashboard │
     │         │Transaction  │           │        └──────────┘
     │         │Consumer     │───────────┘          (3000)
     │         ├─────────────┤
     │         │Alert        │─────┐
     │         │Consumer     │     │
     │         └─────────────┘     │
     │                             │
     │                             ▼
     │                   ┌──────────────────┐
     │                   │ Notification     │
     │                   │ Channels         │
     │                   ├──────────────────┤
     │                   │ • Email (SMTP)   │
     │                   │ • Telegram Bot   │
     │                   │ • Webhook (HTTP) │
     │                   └──────────────────┘
     │
     └──────────────────────────────────────────────────┐
                                                        │
                 ┌──────────────────────────────────────┘
                 ▼
        ┌──────────────────┐
        │  ML Engine       │
        ├──────────────────┤
        │ • ONNX Runtime   │
        │ • Feature Builder│
        │ • CatBoost Model │
        │   (production-15f)│
        │ • ~15ms inference│
        └──────────────────┘
```

---

## Основные компоненты

### 1. API Gateway (Spring Boot)
- **Роль:** Точка входа для всех запросов
- **Endpoints:**
  - `POST /api/transactions` - прием транзакций
  - `GET /api/transactions` - получение списка с фильтрацией
  - `GET /api/transactions/{id}` - детали транзакции
  - `GET /api/transactions/stats` - статистика
  - CRUD `/api/rules` - управление правилами
  - `/api/audit-logs` - журнал изменений
- **Безопасность:** Spring Security (ADMIN, VIEWER роли)

### 2. Очередь сообщений (Apache Kafka)
- **Топики:**
  - `input-transactions` - входящие транзакции
  - `alert-topic` - алерты о подозрительных транзакциях
  - `input-transactions-dlq` - Dead Letter Queue
- **Конфигурация:** 3 брокера, репликация factor=3, ISR=2
- **Гарантии:** At-least-once delivery, idempotence

### 3. Движок правил (Rule Engine)
Модульная система обнаружения мошенничества с 4 типами правил:

#### 3.1 Threshold Rules
```json
{
  "field": "amount",
  "operator": ">",
  "value": 100000
}
```
Простые пороговые правила (amount > X, geo != Y, etc.)

#### 3.2 Pattern Rules
```json
{
  "pattern": "HIGH_VELOCITY",
  "threshold": 5,
  "windowMinutes": 10
}
```
Анализ паттернов поведения (серия мелких транзакций, ночные операции)

#### 3.3 Composite Rules
```json
{
  "operator": "AND",
  "conditions": [
    {"field": "amount", "operator": ">", "value": 50000},
    {"field": "hour", "operator": ">=", "value": 22}
  ]
}
```
Комбинация условий с булевой логикой (AND/OR/NOT)

#### 3.4 ML Rules
```json
{
  "modelPath": "ml/fraud_model_production_15f.onnx",
  "threshold": 0.9,
  "fallbackAction": "PASS"
}
```
Machine Learning модель на базе CatBoost + ONNX Runtime

**ML Feature Engineering (15 признаков):**
- `amount` - сумма транзакции
- `transaction_type` - тип (deposit/payment/transfer/withdrawal)
- `merchant_category` - категория мерчанта
- `location` - геолокация
- `device_used` - тип устройства
- `time_since_last` - время с последней транзакции
- `spending_deviation` - отклонение от среднего
- `velocity` - количество транзакций за 10 минут
- `payment_channel` - канал (online/atm/pos/mobile)
- `amount_on_time` - сумма × время суток
- + 5 дополнительных признаков

**Performance:** ~15ms (5ms features + 10ms inference)

### 4. База данных (PostgreSQL)
**Схема:**
- `transactions` - транзакции (id, correlation_id, source_id, destination_id, amount, type, status, ml_fraud_score, triggered_rules)
- `rules` - правила обнаружения (id, name, rule_type, params_json, priority, enabled)
- `transaction_logs` - структурированные логи событий
- `audit_logs` - журнал изменений правил
- `users` - пользователи системы
- `alert_messages` - уведомления

**Миграции:** Flyway для версионирования схемы

### 5. Кэш (Redis)
- **Idempotency cache:** Хранение ответов по `Idempotency-key` (TTL 30 мин)
- **Transaction history:** In-memory кэш для feature engineering
- **Rules cache:** Hot-reload без перезапуска приложения

### 6. Система уведомлений (Alert Engine)
**Поддерживаемые каналы:**
- **Email (SMTP):** Отправка через JavaMail API
- **Telegram Bot:** Интеграция через Telegram Bot API
- **Webhook:** HTTP POST на внешние системы (Slack, Discord, etc.)

**Функции:**
- **Шаблонизация:** Настраиваемые шаблоны сообщений с подстановкой данных
- **Retry логика:** Автоматические повторные попытки при ошибках (exponential backoff)
- **Дедупликация:** Защита от дублирующих уведомлений (Redis cache, TTL 5 мин)
- **Маршрутизация:** Отправка в разные каналы в зависимости от критичности
- **Rate limiting:** Ограничение частоты уведомлений для одного получателя

**Шаблон уведомления:**
```
🚨 Подозрительная транзакция обнаружена!

ID: {correlationId}
От: {sourceId}
Кому: {destinationId}
Сумма: {amount} RUB
Время: {timestamp}

Сработавшие правила:
{triggeredRules}

ML Fraud Score: {mlScore}

Подробности: http://localhost:8081/dashboard.html?tx={id}
```

### 7. Мониторинг
- **Prometheus:** Сбор метрик (executions, triggers, errors, latency)
- **Grafana:** Визуализация метрик и дашборды
- **Actuator:** `/actuator/prometheus`, `/actuator/health`

### 8. Админ-панель
- **Технология:** HTML + CSS + Vanilla JavaScript
- **Функции:**
  - Управление правилами (CRUD)
  - Просмотр транзакций с фильтрацией
  - Статистика и метрики
  - Экспорт в CSV
  - Журнал аудита
- **Аутентификация:** Basic Auth через Spring Security

---

## Потоки данных

### Поток 1: Прием и обработка транзакции

```
1. External System → POST /api/transactions + Idempotency-key
                     ↓
2. TransactionController → Проверка idempotency cache (Redis)
                     ↓
3. TransactionService → Отправка в Kafka topic "input-transactions"
                     ↓
4. Return 202 Accepted {correlationId, status: "ACCEPTED"}
                     ↓
5. TransactionConsumer → Получение из Kafka
                     ↓
6. TransactionProcessingService → Применение правил
                     ↓
7. RuleEngineService → Выполнение evaluators (Threshold, Pattern, Composite, ML)
                     ↓
8. MlRuleEvaluator → Feature building → ONNX inference → Score comparison
                     ↓
9. Обновление статуса: PENDING → PROCESSED / ALERTED
                     ↓
10. Если ALERTED → Отправка в Kafka topic "alert-topic"
                     ↓
11. AlertMessagesConsumer → Получение алерта
                     ↓
12. AlertEngineService → Формирование уведомлений из шаблонов
                     ↓
13. Параллельная отправка:
    • Email via SMTP (retry + backoff)
    • Telegram Bot API (rate limiting)
    • Webhook POST (timeout 5s)
                     ↓
14. Дедупликация через Redis (TTL 5 мин)
                     ↓
15. Сохранение в БД (transactions, transaction_logs, alert_messages)
                     ↓
16. Метрики: notification_sent, notification_failed, notification_latency
```

### Поток 2: Управление правилами

```
1. Admin → POST /api/rules (создание нового правила)
              ↓
2. RuleController → Валидация params_json
              ↓
3. RuleService → Сохранение в БД
              ↓
4. AuditLogService → Запись в журнал аудита
              ↓
5. RuleEngineService.reloadRulesCache() → Атомарное обновление кэша
              ↓
6. Новое правило применяется к следующим транзакциям БЕЗ ПЕРЕЗАПУСКА
```

### Поток 3: Просмотр транзакций

```
1. Admin → GET /api/transactions?status=ALERTED&page=0&size=10
              ↓
2. TransactionController → AdminTransactionService
              ↓
3. Query PostgreSQL с фильтрацией и пагинацией
              ↓
4. Return PagedResponse<TransactionResponseDto>
              ↓
5. Админ-панель → Отображение в таблице + детальная карточка
```

---

## Технологический стек

| Компонент | Технология | Версия |
|-----------|-----------|--------|
| Backend | Java + Spring Boot | 3.5.6 |
| Build Tool | Maven | - |
| Database | PostgreSQL | 15 |
| Message Queue | Apache Kafka | 3.8.1 |
| Cache | Redis | 7 Alpine |
| ML Runtime | ONNX Runtime | 1.19.2 |
| ML Model | CatBoost (ONNX format) | production-15f |
| Migrations | Flyway | - |
| Security | Spring Security | - |
| Metrics | Prometheus + Grafana | latest |
| Rate Limiting | Bucket4j | 8.7.0 |
| Serialization | Jackson JSON | - |
| Email | Spring Mail (SMTP) | - |
| Telegram Bot | Telegram Bot API | - |
| HTTP Client | Spring WebClient | - |
| Frontend | HTML + CSS + Vanilla JS | - |

---

## Ключевые особенности

### 🚀 Performance
- **ML Inference:** ~15ms (5ms features + 10ms inference)
- **Throughput:** Поддержка 10,000 concurrent транзакций
- **Latency:** Sub-second обработка транзакций
- **Kafka:** Репликация 3x для high availability

### 🔒 Reliability
- **Idempotency:** Защита от дубликатов через Redis cache
- **At-least-once delivery:** Kafka гарантии (`acks=all`, `retries=3`)
- **Graceful degradation:** ML fallback при ошибках (PASS/ALERTED)
- **Correlation ID:** Сквозная трассировка через все компоненты

### 🔧 Flexibility
- **Hot-reload правил:** Обновление без перезапуска (copy-on-write)
- **Pluggable evaluators:** Легко добавить новые типы правил
- **Configurable thresholds:** ML threshold настраивается через админ-панель
- **Rule priority:** Детерминированный порядок исполнения

### 📊 Observability
- **Structured logs:** Correlation ID + component tags
- **Prometheus metrics:** Executions, triggers, errors, latency
- **Grafana dashboards:** Real-time визуализация
- **Audit trail:** Полная история изменений правил

### 🛡️ Security
- **Spring Security:** RBAC (ADMIN, VIEWER роли)
- **Bean Validation:** Валидация входных данных
- **Rate Limiting:** Защита от DDoS (Bucket4j)
- **Prepared Statements:** SQL injection защита (JPA)

---

## Масштабируемость

### Horizontal Scaling
- **API Layer:** Stateless, можно запустить N инстансов за load balancer
- **Kafka:** 3 брокера, можно добавить больше
- **PostgreSQL:** Read replicas для чтения
- **Redis:** Redis Cluster для горизонтального масштабирования

### Vertical Scaling
- **ML Model:** Легковесная ONNX модель (544 KB)
- **Memory:** In-memory history store для быстрого feature engineering
- **CPU:** Параллельная обработка через Kafka partitions

---

## Развертывание

```bash
# 1. Запуск инфраструктуры
docker-compose up -d

# 2. Сборка приложения
cd AdminApi
mvn clean package -DskipTests

# 3. Запуск приложения
java -jar target/AdminApi-0.0.1-SNAPSHOT.jar

# 4. Доступ к сервисам
# API: http://localhost:8081
# Grafana: http://localhost:3000 (admin/admin)
# Prometheus: http://localhost:9090
# Kafka UI: http://localhost:8080
```

**Компоненты в docker-compose:**
- Kafka кластер (3 брокера): 9094, 9095, 9096
- PostgreSQL: 5433
- Redis: 6379
- Prometheus: 9090
- Grafana: 3000
- Kafka UI: 8080

---

## Примеры использования

### Создание транзакции
```bash
curl -X POST http://localhost:8081/api/transactions \
  -H "Content-Type: application/json" \
  -H "Idempotency-key: tx-12345" \
  -d '{
    "from": "account-1234",
    "to": "account-5678",
    "amount": 150000,
    "type": "transfer",
    "timestamp": "2025-01-26T12:30:00",
    "geo": "Moscow",
    "channel": "online"
  }'
```

### Создание ML правила
```bash
curl -X POST http://localhost:8081/api/rules \
  -H "Content-Type: application/json" \
  -u admin:admin123 \
  -d '{
    "name": "ML Fraud Detection",
    "description": "CatBoost model with 0.9 threshold",
    "ruleType": "ML",
    "priority": 1,
    "enabled": true,
    "paramsJson": "{\"threshold\": 0.9, \"fallbackAction\": \"PASS\"}"
  }'
```

### Получение статистики
```bash
curl http://localhost:8081/api/transactions/stats \
  -u admin:admin123
```

---

## Архитектурные принципы

1. **Separation of Concerns:** Четкое разделение на слои (Controller → Service → Repository)
2. **Strategy Pattern:** Pluggable rule evaluators через `RuleEvaluatorFactory`
3. **Copy-on-Write:** Атомарное обновление кэша правил
4. **Circuit Breaker:** Graceful degradation при ошибках ML
5. **Event-Driven:** Асинхронная обработка через Kafka
6. **CQRS-lite:** Разделение команд (write) и запросов (read)
7. **Fail-Fast:** Ранняя валидация входных данных

---

## Метрики системы

### Транзакции
- **transactions_total:** Counter - всего обработанных транзакций
- **transactions_alerted:** Counter - помеченных как подозрительные
- **transactions_processed:** Counter - успешно обработанных
- **transaction_processing_time:** Histogram - время обработки

### Правила
- **rule_executions_total:** Counter per rule - выполнений правила
- **rule_triggers_total:** Counter per rule - срабатываний правила
- **rule_errors_total:** Counter per rule - ошибок правила
- **rule_execution_time:** Histogram per rule - время выполнения

### ML модель
- **ml_inference_time:** Histogram - время inference
- **ml_feature_build_time:** Histogram - время построения признаков
- **ml_scores:** Histogram - распределение ML scores
- **ml_errors_total:** Counter - ошибки ML

### Уведомления
- **notifications_sent_total:** Counter per channel - отправлено уведомлений
- **notifications_failed_total:** Counter per channel - ошибки отправки
- **notification_delivery_time:** Histogram per channel - время доставки
- **notification_retry_count:** Counter - количество retry
- **notification_deduplicated:** Counter - дедуплицированных уведомлений

### Инфраструктура
- **kafka_consumer_lag:** Gauge per consumer group - отставание consumers
- **redis_cache_hit_ratio:** Gauge - коэффициент попаданий в кэш
- **redis_cache_size:** Gauge - размер кэша
- **api_requests_total:** Counter per endpoint - запросов к API
- **api_latency:** Histogram per endpoint - задержка API

---

*Документ создан: 2025-01-26*
*Версия: 1.0*
