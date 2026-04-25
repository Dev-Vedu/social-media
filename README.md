# Social API

Spring Boot microservice with Redis guardrails.

## My Setup

- **PostgreSQL** — installed locally on my laptop (not Docker)
- **Redis** — running via Docker
- **Spring Boot** — runs via IntelliJ

---

## How to Run

**Step 1 - Start Redis (Docker)**
```
docker-compose up -d
```

**Step 2 - Run the app in IntelliJ**

Open the project in IntelliJ and click the green ▶ button on `SocialApiApplication.java`

App runs at: http://localhost:8080

**Step 3 - Insert seed data into PostgreSQL**

Open pgAdmin or psql and run:
```sql
INSERT INTO users (username, is_premium) VALUES ('vedu', true);
INSERT INTO bots (name, persona_description) VALUES ('YashBot', 'Friendly bot');
```

---

## application.properties

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/socialdb
spring.datasource.username=postgres
spring.datasource.password=YOUR_LOCAL_POSTGRES_PASSWORD
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

> Note: PostgreSQL is installed locally on the machine. Only Redis uses Docker.

---

## docker-compose.yml (Redis only)

```yaml
version: '3.8'
services:
  redis:
    image: redis:7-alpine
    container_name: social-redis
    ports:
      - "6379:6379"
```

---

## Endpoints

| Method | URL | What it does |
|--------|-----|--------------|
| POST | /api/posts | Create a post |
| POST | /api/posts/{id}/comments | Add a comment |
| POST | /api/posts/{id}/like | Like a post |

---

## Redis Keys Used

| Key | Purpose |
|-----|---------|
| post:{id}:virality_score | Virality score (Bot reply +1, Like +20, Comment +50) |
| post:{id}:bot_count | How many bots replied (max 100) |
| cooldown:bot_{id}:human_{id} | Bot cooldown per human (10 min TTL) |
| notif_cooldown:user_{id} | Notification cooldown (15 min TTL) |
| user:{id}:pending_notifs | Buffered notifications list |

---

## How Thread Safety Works (Atomic Locks)

**Horizontal Cap (100 bot replies):**
A Lua script is used in Redis. The Lua script does the check AND the increment in one single atomic step. This means even if 200 bots try at the same time, Redis handles them one by one and stops exactly at 100. Without Lua, two bots could both read "99" and both pass the check — ending up at 101.

**Cooldown Cap:**
Uses Redis `SET NX` (set if not exists) with a TTL. This is a single atomic command so two bots cannot both win the cooldown check at the same time.

---

## How to Stop

**Stop IntelliJ app** — click red ⏹ button

**Stop Redis Docker:**
```
docker-compose down
```

## How to Start Again Next Time

```
docker-compose up -d
```
Then click green ▶ button in IntelliJ.

> PostgreSQL starts automatically with your laptop so no need to start it manually.
