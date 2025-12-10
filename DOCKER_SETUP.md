# 🐳 Docker Setup - Fraud Detection System

Полная инструкция по сборке и запуску приложения в Docker контейнерах.

## 📋 Содержание

- [Требования](#требования)
- [Структура](#структура)
- [Быстрый старт](#быстрый-старт)
- [Подробная инструкция](#подробная-инструкция)
- [Доступ к сервисам](#доступ-к-сервисам)
- [Полезные команды](#полезные-команды)
- [Troubleshooting](#troubleshooting)

## 🔧 Требования

Убедитесь, что установлены:
- **Docker** >= 20.10
- **Docker Compose** >= 2.0
- Минимум **4GB RAM** для Docker
- Минимум **10GB** свободного места на диске

Проверить установку:
```bash
docker --version
docker-compose --version
```

## 📁 Структура

```
.
├── AdminApi/
│   ├── Dockerfile              # Dockerfile для приложения
│   ├── .dockerignore           # Исключения для Docker build
│   └── src/                    # Исходный код (Backend + Frontend)
├── docker-compose.yml          # Конфигурация всей инфраструктуры
└── DOCKER_SETUP.md            # Эта инструкция
```

## 🚀 Быстрый старт

### Запуск всей инфраструктуры одной командой:

```bash
docker-compose up -d
```

Эта команда запустит:
- ✅ Kafka кластер (3 брокера)
- ✅ PostgreSQL база данных
- ✅ Redis кэш
- ✅ Admin API (Backend + Frontend)
- ✅ Kafka UI (веб-интерфейс для Kafka)
- ✅ Prometheus (метрики)
- ✅ Grafana (дашборды)

### Проверка статуса:

```bash
docker-compose ps
```

### Остановка всех сервисов:

```bash
docker-compose down
```

## 📖 Подробная инструкция

### 1. Первый запуск (с полной сборкой)

```bash
# Собрать образы и запустить контейнеры
docker-compose up -d --build

# Просмотр логов в реальном времени
docker-compose logs -f admin-api
```

**Время первого запуска:** ~3-5 минут (загрузка образов и сборка приложения)

### 2. Проверка готовности сервисов

```bash
# Проверить health check приложения
curl http://localhost:8081/actuator/health

# Проверить PostgreSQL
docker exec -it postgres-admin-db pg_isready -U admin

# Проверить Redis
docker exec -it redis-cache redis-cli ping
```

### 3. Остановка с сохранением данных

```bash
# Остановить контейнеры (данные сохранятся в volumes)
docker-compose down
```

### 4. Полная очистка (включая данные)

```bash
# Остановить и удалить все данные
docker-compose down -v

# Удалить все образы
docker-compose down --rmi all
```

## 🌐 Доступ к сервисам

После запуска `docker-compose up -d`, сервисы доступны по адресам:

| Сервис | URL | Описание |
|--------|-----|----------|
| **Admin Dashboard** | http://localhost:8081 | Главное приложение (Frontend + Backend) |
| **Health Check** | http://localhost:8081/actuator/health | Проверка здоровья приложения |
| **Prometheus Metrics** | http://localhost:8081/actuator/prometheus | Метрики для мониторинга |
| **Kafka UI** | http://localhost:8080 | Веб-интерфейс для Kafka |
| **Prometheus** | http://localhost:9090 | Система мониторинга |
| **Grafana** | http://localhost:3000 | Дашборды (admin/admin) |
| **PostgreSQL** | localhost:5433 | База данных (admin/signIn) |
| **Redis** | localhost:6379 | Кэш |

### Учетные данные

**Admin Dashboard:**
- Username: `admin`
- Password: `admin`

**Grafana:**
- Username: `admin`
- Password: `admin`

**PostgreSQL:**
- Database: `AdminBD`
- Username: `admin`
- Password: `signIn`

## 🛠 Полезные команды

### Просмотр логов

```bash
# Все логи
docker-compose logs -f

# Только приложение
docker-compose logs -f admin-api

# Только Kafka
docker-compose logs -f kafka-0 kafka-1 kafka-2

# Последние 100 строк
docker-compose logs --tail=100 admin-api
```

### Перезапуск сервисов

```bash
# Перезапустить приложение
docker-compose restart admin-api

# Перезапустить всё
docker-compose restart
```

### Пересборка приложения

```bash
# Если изменили код - пересобрать образ
docker-compose build admin-api

# Пересобрать и перезапустить
docker-compose up -d --build admin-api
```

### Подключение к контейнерам

```bash
# Shell в контейнере приложения
docker exec -it fraud-detection-admin-api sh

# PostgreSQL CLI
docker exec -it postgres-admin-db psql -U admin -d AdminBD

# Redis CLI
docker exec -it redis-cache redis-cli
```

### Мониторинг ресурсов

```bash
# Использование ресурсов контейнерами
docker stats

# Использование дискового пространства
docker system df
```

### Очистка неиспользуемых ресурсов

```bash
# Удалить остановленные контейнеры
docker container prune

# Удалить неиспользуемые образы
docker image prune

# Удалить всё неиспользуемое (осторожно!)
docker system prune -a
```

## 🔍 Troubleshooting

### Приложение не запускается

**Проблема:** Контейнер `admin-api` постоянно перезагружается

**Решение:**
```bash
# Проверить логи
docker-compose logs admin-api

# Проверить, что БД готова
docker-compose logs postgres

# Попробовать перезапустить
docker-compose restart admin-api
```

### Ошибка подключения к Kafka

**Проблема:** `Connection refused` к Kafka

**Решение:**
```bash
# Проверить, что все брокеры запущены
docker-compose ps kafka-0 kafka-1 kafka-2

# Подождать ~30 секунд после запуска Kafka
# Перезапустить приложение
docker-compose restart admin-api
```

### Нехватка памяти

**Проблема:** `OutOfMemoryError` в контейнере

**Решение:**
Увеличьте лимиты памяти в `docker-compose.yml`:
```yaml
admin-api:
  environment:
    JAVA_OPTS: -Xms512m -Xmx2048m  # Увеличить до 2GB
```

### Порты заняты

**Проблема:** `port is already allocated`

**Решение:**
```bash
# Найти процесс, использующий порт (например, 8081)
# Windows:
netstat -ano | findstr :8081

# Linux/Mac:
lsof -i :8081

# Остановить процесс или изменить порт в docker-compose.yml
```

### Данные потеряны после перезапуска

**Проблема:** После `docker-compose down` данные исчезли

**Объяснение:** Volumes сохраняются при `docker-compose down`

**Проверка volumes:**
```bash
# Список всех volumes
docker volume ls

# Инспекция volume
docker volume inspect <volume_name>
```

### Медленная сборка

**Проблема:** `docker-compose build` занимает много времени

**Решение:**
```bash
# Использовать BuildKit для ускорения
DOCKER_BUILDKIT=1 docker-compose build

# Или установить в переменные окружения
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1
```

## 📊 Проверка работоспособности

После запуска проверьте:

1. **Health Check приложения:**
   ```bash
   curl http://localhost:8081/actuator/health
   ```
   Ожидаемый ответ: `{"status":"UP"}`

2. **Доступность UI:**
   Откройте http://localhost:8081 в браузере

3. **Kafka топики:**
   - Откройте http://localhost:8080
   - Проверьте наличие топиков: `alert-topic`, `input-transactions`

4. **База данных:**
   ```bash
   docker exec -it postgres-admin-db psql -U admin -d AdminBD -c "\dt"
   ```

## 📝 Примечания

- **Первый запуск:** Flyway автоматически создаст схему БД
- **ML модель:** Загружается из `src/main/resources/ml/`
  - **Важно:** Используется ONNX Runtime, требуется Debian-based образ (не Alpine)
  - Dockerfile использует `eclipse-temurin:17-jre-jammy` для совместимости
- **Фронтенд:** Статические файлы в `src/main/resources/static/`
- **Конфигурация:** Переменные окружения переопределяют `application.properties`
- **Размер образа:** ~400MB (из-за glibc и ONNX Runtime зависимостей)

## 🔄 Обновление приложения

После изменения кода:

```bash
# 1. Пересобрать образ
docker-compose build admin-api

# 2. Пересоздать контейнер
docker-compose up -d --force-recreate admin-api

# Или одной командой:
docker-compose up -d --build --force-recreate admin-api
```

## 📞 Поддержка

При возникновении проблем:
1. Проверьте логи: `docker-compose logs admin-api`
2. Проверьте health check: `curl http://localhost:8081/actuator/health`
3. Убедитесь, что все зависимости запущены: `docker-compose ps`
