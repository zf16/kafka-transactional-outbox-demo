# Kafka Transactional Outbox Demo Runbook

## 1. Overview

这份 Runbook 用于按固定顺序演示 PostgreSQL transaction、Transactional Outbox Pattern、Kafka、consumer retry 和 idempotency。

业务 API 和检查 API 使用 Postman。Terminal 只用于启动环境、查看日志、运行 Kafka CLI 和执行必要的数据库命令。

## 2. 架构和端口

```text
Postman
  |
  | POST /api/v1/flights
  v
Flight Service :8080
  |
  | one PostgreSQL transaction
  +--> flight_service.flights
  +--> flight_service.flight_outbox
                    |
                    | poll every 10 seconds
                    v
        Event Processing Service :8081
                    |
                    v
           Kafka topic: flight.events
                    |
                    v
          Payment Service :8082
                    |
                    +--> payment_service.processed_events
                    +--> simulated payment log
```

| Component | Address/port |
| --- | --- |
| Flight Service | `http://localhost:8080` |
| Event Processing Service | `http://localhost:8081` |
| Payment Service | `http://localhost:8082` |
| PostgreSQL | `localhost:5432` |
| Kafka from the host | `localhost:9092` |
| Kafka inside Docker network | `kafka:29092` |
| Kafka topic | `flight.events` |
| Payment consumer group | `payment-service` |

这个本地环境只有一个 Kafka broker、一个 `flight.events` partition 和 replication factor 1。它适合演示消息流程，但不能演示 broker failover。

## 3. Prerequisites

启动前确认已经安装：

- Docker Desktop，并支持 `docker compose`。
- Postman。
- pgAdmin 4。

进入项目目录：

```bash
cd /Users/lzf/Training/Kafka/Demo
```

## 4. Optional: Reset to a Clean Environment

如果需要清除上一次演示的 PostgreSQL 和 Kafka 数据：

```bash
docker compose down --volumes
```

> `--volumes` 会删除 demo PostgreSQL volume。只在确认不需要保留数据时执行。

Kafka 在这个 Compose 文件中没有持久化 volume，因此重新创建 Kafka container 也会重新创建 topic data。

## 5. Start All Services

后台构建并启动：

```bash
docker compose up --build -d
```

检查 containers：

```bash
docker compose ps
```

预期以下 services 处于运行状态：

```text
postgres
kafka
flight-service
event-processing-service
payment-service
```

`kafka-init` 是一次性初始化任务。它成功创建 `flight.events` 后退出是正常现象。

查看重要日志：

```bash
docker compose logs -f \
  flight-service \
  event-processing-service \
  payment-service
```

按 `Ctrl-C` 只会退出 log follow，不会停止 containers。

## 6. Postman Requests

不需要配置 Postman environment。直接创建下面几个 requests。

### Create Flight

- Method: `POST`
- Endpoint: `http://localhost:8080/api/v1/flights`
- Payload：

```json
{
  "departureAirport": "Paris",
  "arrivalAirport": "London",
  "departureDateTime": "2026-11-01T08:00:00Z",
  "arrivalDateTime": "2026-11-01T09:15:00Z"
}
```

在 Postman 中选择 **Body → raw → JSON**。预期 response 为 `201 Created`。

### Get Flight

- Method: `GET`
- Endpoint: `http://localhost:8080/api/v1/flights/{flightId}`
- Payload：无

把 `{flightId}` 替换成 Create Flight response 中返回的 flight ID。

### List Outbox Events

- Method: `GET`
- Endpoint: `http://localhost:8080/api/v1/internal/outbox-events`
- Payload：无

每个 outbox event 包含 `processedAt` 和 `status`：

- `PENDING`：`processedAt` 为 `null`，relay 尚未成功完成发布。
- `PROCESSED`：`processedAt` 有值，Kafka 发布已成功。

### Health Checks

| Service | Method | Endpoint | Payload |
| --- | --- | --- | --- |
| Flight Service | `GET` | `http://localhost:8080/actuator/health` | 无 |
| Event Processing Service | `GET` | `http://localhost:8081/actuator/health` | 无 |
| Payment Service | `GET` | `http://localhost:8082/actuator/health` | 无 |

预期 response 为 `200 OK`，body 为 `{"status":"UP"}`。

## 7. Configure pgAdmin

在 pgAdmin 中选择 **Register → Server**。

### General

| Setting | Value |
| --- | --- |
| Name | `Kafka Outbox Demo` |

### Connection

| Setting | Value |
| --- | --- |
| Host name/address | `localhost` |
| Port | `5432` |
| Maintenance database | `outbox_demo` |
| Username | `outbox` |
| Password | `outbox` |

数据库结构：

```text
outbox_demo
└── Schemas
    ├── flight_service
    │   └── Tables
    │       ├── flights
    │       └── flight_outbox
    └── payment_service
        └── Tables
            └── processed_events
```

常用 SQL：

```sql
SELECT *
FROM flight_service.flights
ORDER BY created_at DESC;
```

```sql
SELECT
    id,
    event_id,
    flight_id,
    event_type,
    payload,
    created_at,
    processed_at,
    CASE
        WHEN processed_at IS NULL THEN 'PENDING'
        ELSE 'PROCESSED'
    END AS status
FROM flight_service.flight_outbox
ORDER BY id DESC;
```

```sql
SELECT *
FROM payment_service.processed_events
ORDER BY processed_at DESC;
```

## 8. Demo A: Atomic Database Write

目标：证明 Flight Service 在一个 PostgreSQL transaction 中同时写入 flight 和 outbox event。

### Step 1: Stop the relay

```bash
docker compose stop event-processing-service
```

这样可以防止 relay 立即把 event 标记为 `PROCESSED`。

### Step 2: Create a flight in Postman

在 Postman 中发送 `POST http://localhost:8080/api/v1/flights`。

预期：

- HTTP response 是 `201 Created`。
- Response 中包含新 flight ID。

### Step 3: Inspect the outbox in Postman

在 Postman 中发送 `GET http://localhost:8080/api/v1/internal/outbox-events`。

找到最新 event，预期：

```json
{
  "status": "PENDING",
  "processedAt": null
}
```

### Step 4: Inspect PostgreSQL in pgAdmin

执行 `flights` 和 `flight_outbox` 查询。确认：

- `flights` 有新 row。
- `flight_outbox` 有对应 row。
- 两张表中的 `flight_id` 相同。
- Outbox 有稳定且唯一的 `event_id`。
- `processed_at` 为 `null`。

结论：业务数据和待发布 event 已经原子提交。Kafka 此时还没有收到 event，但 event 没有丢失。

## 9. Demo B: Complete the Happy Path

### Step 1: Follow logs

```bash
docker compose logs -f event-processing-service payment-service
```

### Step 2: Start the relay

在另一个 Terminal：

```bash
docker compose start event-processing-service
```

Poll interval 是 10 秒。等待日志出现类似内容：

```text
Polled 1 pending outbox event(s)
Publishing outbox event: eventId=... flightId=...
Outbox event processed: eventId=... topic=flight.events partition=0 offset=...
Processing payment for flight ... (eventId=...)
```

### Step 3: Check the outbox again in Postman

再次发送 `GET http://localhost:8080/api/v1/internal/outbox-events`。

同一个 event 现在应该是：

```json
{
  "status": "PROCESSED",
  "processedAt": "..."
}
```

Outbox row 为了审计和课堂观察会被保留，而不是 hard delete。Relay 只轮询 `processed_at IS NULL` 的 rows，因此已经处理的 row 不会再次进入正常 batch。

### Step 4: Check the consumer idempotency table

在 pgAdmin 执行：

```sql
SELECT *
FROM payment_service.processed_events
ORDER BY processed_at DESC;
```

应该看到对应的 `event_id`。

## 10. Inspect Kafka

以下 Kafka CLI commands 都在 `kafka` container 中运行，所以 bootstrap server 使用 `localhost:29092`。

### Check broker count

```bash
docker compose exec -T kafka \
  /opt/kafka/bin/kafka-broker-api-versions.sh \
  --bootstrap-server localhost:29092
```

只计算 broker header：

```bash
docker compose exec -T kafka \
  /opt/kafka/bin/kafka-broker-api-versions.sh \
  --bootstrap-server localhost:29092 \
  | grep -c '(id:'
```

当前 demo 预期输出：

```text
1
```

### Inspect KRaft metadata quorum

```bash
docker compose exec -T kafka \
  /opt/kafka/bin/kafka-metadata-quorum.sh \
  --bootstrap-server localhost:29092 \
  describe --status
```

### List topics

```bash
docker compose exec -T kafka \
  /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:29092 \
  --list
```

应该至少看到：

```text
flight.events
```

### Describe topic and partitions

```bash
docker compose exec -T kafka \
  /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:29092 \
  --describe \
  --topic flight.events
```

预期：

```text
PartitionCount: 1
ReplicationFactor: 1
```

Detail 中还会显示 `Partition`、`Leader`、`Replicas` 和 `Isr`。

### Check latest offsets

```bash
docker compose exec -T kafka \
  /opt/kafka/bin/kafka-get-offsets.sh \
  --bootstrap-server localhost:29092 \
  --topic flight.events \
  --time -1
```

例如：

```text
flight.events:0:3
```

表示 partition 0 当前的 log-end offset 是 3，不代表每个 consumer group 都已经处理到这里。

### Read messages with metadata

```bash
docker compose exec -T kafka \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:29092 \
  --topic flight.events \
  --from-beginning \
  --property print.key=true \
  --property print.partition=true \
  --property print.offset=true
```

按 `Ctrl-C` 退出。Console consumer 读取消息不会将消息从 Kafka 删除。

### Inspect consumer group and lag

```bash
docker compose exec -T kafka \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:29092 \
  --describe \
  --group payment-service
```

重点观察：

```text
CURRENT-OFFSET
LOG-END-OFFSET
LAG
```

关系为：

```text
LAG = LOG-END-OFFSET - CURRENT-OFFSET
```

## 11. Demo C: Kafka Unavailable

目标：证明 Kafka 暂时不可用不会让已经提交的业务 event 丢失。

### Step 1: Stop Kafka

```bash
docker compose stop kafka
```

### Step 2: Create another flight in Postman

在 Postman 中向 `POST http://localhost:8080/api/v1/flights` 发送另一个航班：

```json
{
  "departureAirport": "Seattle",
  "arrivalAirport": "Chicago",
  "departureDateTime": "2026-11-02T15:00:00Z",
  "arrivalDateTime": "2026-11-02T19:00:00Z"
}
```

发送 request。预期仍然是 `201 Created`，因为 Flight Service transaction 只依赖 PostgreSQL。

### Step 3: Check the outbox in Postman

发送 `GET http://localhost:8080/api/v1/internal/outbox-events`。新 event 应该保持：

```text
status = PENDING
processedAt = null
```

Event Processing Service 会记录 Kafka publication failure，并在下一次 poll 重试。

### Step 4: Restart Kafka

```bash
docker compose start kafka
```

等待 Kafka healthcheck 完成以及下一次 10 秒 poll。观察日志：

```bash
docker compose logs -f event-processing-service payment-service
```

### Step 5: Verify recovery

在 Postman 再次发送 `GET http://localhost:8080/api/v1/internal/outbox-events`。新 event 应变为 `PROCESSED`，Payment Service 日志应出现对应的 payment processing。

不需要重新发送 `POST http://localhost:8080/api/v1/flights`。

## 12. Demo D: Consumer Lag

### Step 1: Stop Payment Service

```bash
docker compose stop payment-service
```

### Step 2: Create two or three flights in Postman

重复发送 `POST http://localhost:8080/api/v1/flights`，可以修改 airport 或时间来区分 requests。

等待 Event Processing Service 完成下一次 poll，将 events 发布到 Kafka。

### Step 3: Inspect lag

```bash
docker compose exec -T kafka \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:29092 \
  --describe \
  --group payment-service
```

此时 `LOG-END-OFFSET` 应该向前增长，而 `CURRENT-OFFSET` 保持不变，因此 `LAG` 大于 0。

### Step 4: Restart Payment Service

```bash
docker compose start payment-service
```

等待 consumer 处理消息，再次 describe group。预期 `LAG` 回到 0。

## 13. Demo E: Duplicate Events and Idempotent Consumer

目标：手工把同一个 event 再发送两次，验证 Payment Service 只执行一次业务处理。

### Step 1: Stop Event Processing Service

```bash
docker compose stop event-processing-service
```

### Step 2: Create a new flight in Postman

发送 `POST http://localhost:8080/api/v1/flights`。不要立即启动 relay。

### Step 3: Copy the event JSON from pgAdmin

```sql
SELECT
    event_id,
    payload::text AS event_json
FROM flight_service.flight_outbox
WHERE processed_at IS NULL
ORDER BY id DESC
LIMIT 1;
```

只复制 `event_json` cell。内容必须：

- 以 `{` 开头、以 `}` 结尾。
- 是完整的单行 JSON。
- 不包含 column header 或 `event_id`。
- 不包含 Markdown code block 标记。
- 不要格式化成多行。

> `kafka-console-producer` 默认把每一行当成一条 Kafka message。粘贴多行 JSON 会产生多条不完整 records，Payment Service 会在反序列化阶段 retry，根本不会到达 idempotency logic。

### Step 4: Process the original event once

```bash
docker compose start event-processing-service
```

查看日志：

```bash
docker compose logs -f event-processing-service payment-service
```

等待出现：

```text
Outbox event processed: eventId=...
Processing payment for flight ... (eventId=...)
```

看到以后按 `Ctrl-C` 退出 log follow。

### Step 5: Open an interactive Kafka producer

这里不要使用 `-T`：

```bash
docker compose exec kafka \
  /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:29092 \
  --topic flight.events
```

### Step 6: Paste the same event twice

1. 粘贴 Step 3 复制的完整单行 JSON，按 `Enter`。
2. 再粘贴一次完全相同的单行 JSON，再按 `Enter`。
3. 按 `Ctrl-D` 退出 producer。

一行 JSON 是一条 Kafka record，因此这里会产生两条相同 `eventId` 的 duplicate records。

### Step 7: Verify logs

```bash
docker compose logs --since=5m payment-service \
  | rg "Processing payment|Skipping duplicate"
```

预期：

```text
Processing payment for flight ... (eventId=...)
Skipping duplicate eventId=... flightId=...
Skipping duplicate eventId=... flightId=...
```

### Step 8: Verify the database

在 pgAdmin 中将值替换为 Step 3 的 event ID：

```sql
SELECT *
FROM payment_service.processed_events
WHERE event_id = 'paste-event-id-here';
```

虽然 Kafka 中有三条相同 `eventId` 的 records，结果仍然只有一行。

Payment Service 的去重逻辑等价于：

```sql
INSERT INTO processed_events (event_id, processed_at)
VALUES (?, ?)
ON CONFLICT (event_id) DO NOTHING;
```

## 14. Troubleshooting

### Postman receives connection refused

检查 containers：

```bash
docker compose ps
```

检查对应 service logs：

```bash
docker compose logs --tail=200 flight-service
```

### Outbox remains PENDING

检查 relay 是否运行：

```bash
docker compose ps event-processing-service
```

检查 relay 日志：

```bash
docker compose logs --tail=250 event-processing-service
```

确认 Kafka 健康：

```bash
docker compose ps kafka
```

Poll interval 是 10 秒，启动后可能需要等待下一轮 poll。

### Payment Service keeps retrying a manually produced record

最常见原因是粘贴了多行 JSON，Kafka producer 将每一行作为独立 message。

正确 event 必须是一个完整的单行 JSON object。不要包含：

- pgAdmin column header。
- SQL output formatting。
- Markdown fences。
- 前后的引号包装。
- 多行缩进。

如果只是课堂 demo，并且需要完全清理错误 record：

```bash
docker compose down --volumes
docker compose up --build -d
```

这会清除 demo 数据并重新创建环境。

### Inspect recent important logs

```bash
docker compose logs --since=10m \
  event-processing-service \
  payment-service \
  | rg "Polled|Publishing|processed:|Processing payment|Skipping duplicate|ERROR"
```

## 15. Stop and Clean Up

停止 containers，但保留 PostgreSQL volume：

```bash
docker compose down
```

停止并删除 PostgreSQL demo data：

```bash
docker compose down --volumes
```

