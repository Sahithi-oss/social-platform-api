# Social Platform API — Backend Engineering Assignment

> **Tech Stack:** Java 17 · Spring Boot 3.2 · PostgreSQL 15 · Redis 7 · Docker Compose

---

## Table of Contents
1. [Quick Start](#quick-start)
2. [Project Structure](#project-structure)
3. [API Reference](#api-reference)
4. [Phase 1 — Database Schema](#phase-1--database-schema)
5. [Phase 2 — Redis Virality Engine & Atomic Locks](#phase-2--redis-virality-engine--atomic-locks)
6. [Phase 3 — Notification Engine](#phase-3--notification-engine)
7. [Thread Safety — How Atomic Locks Work](#thread-safety--how-atomic-locks-work)
8. [Running the Concurrency Test](#running-the-concurrency-test)

---

## Quick Start

### Prerequisites
- Docker Desktop (for Postgres + Redis)
- Java 17+
- Maven 3.8+

### 1. Start infrastructure
```bash
docker-compose up -d
```
This brings up:
- **PostgreSQL** on `localhost:5432` (db: `social_platform`, user/pass: `postgres`)
- **Redis** on `localhost:6379`

### 2. Run the application
```bash
mvn spring-boot:run
```
The app starts on **http://localhost:8080**.

On first startup the `DataSeeder` automatically inserts:
- **3 Users:** alice (id 1), bob (id 2), charlie (id 3)
- **3 Bots:** NovaSpark (id 1), EchoMind (id 2), ViraByte (id 3)

### 3. Test with Postman
Import `Social_Platform_API.postman_collection.json` and run the requests in order.

---

## Project Structure

```
src/main/java/com/socialplatform/
├── SocialPlatformApiApplication.java   # Entry point + @EnableScheduling
├── config/
│   ├── RedisConfig.java                # RedisTemplate<String,String> bean
│   └── DataSeeder.java                 # Sample data on startup
├── model/
│   ├── User.java                       # Human user entity
│   ├── Bot.java                        # AI bot entity
│   ├── Post.java                       # Post (User or Bot authored)
│   ├── Comment.java                    # Comment with depth_level
│   └── AuthorType.java                 # Enum: USER | BOT
├── repository/
│   ├── UserRepository.java
│   ├── BotRepository.java
│   ├── PostRepository.java
│   └── CommentRepository.java
├── dto/
│   ├── CreatePostRequest.java
│   ├── CreateCommentRequest.java
│   ├── LikePostRequest.java
│   └── ApiResponse.java                # Generic JSON wrapper
├── exception/
│   ├── GuardrailException.java         # 429 Too Many Requests
│   ├── ResourceNotFoundException.java  # 404 Not Found
│   └── GlobalExceptionHandler.java     # @RestControllerAdvice
├── service/
│   ├── ViralityService.java            # Redis INCR for scoring
│   ├── GuardrailService.java           # Atomic locks (all 3 caps)
│   ├── NotificationService.java        # Smart batching
│   ├── PostService.java                # Core business logic
│   └── UserService.java                # User/Bot creation
├── controller/
│   ├── PostController.java             # /api/posts endpoints
│   └── UserController.java             # /api/users, /api/bots
└── scheduler/
    └── NotificationScheduler.java      # CRON sweeper (every 5 min)
```

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/users` | Create a user (`?username=alice&isPremium=true`) |
| `POST` | `/api/bots` | Create a bot (`?name=NovaSpark`) |
| `POST` | `/api/posts` | Create a new post |
| `POST` | `/api/posts/{postId}/comments` | Add a comment (guardrails active for BOT) |
| `POST` | `/api/posts/{postId}/like` | Like a post (human only) |
| `GET`  | `/api/posts/{postId}/virality` | Get real-time virality score from Redis |

---

## Phase 1 — Database Schema

### `users`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| username | VARCHAR(100) UNIQUE | |
| is_premium | BOOLEAN | default false |

### `bots`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| name | VARCHAR(100) | |
| persona_description | TEXT | |

### `posts`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| author_id | BIGINT | FK → users.id **or** bots.id |
| author_type | VARCHAR(10) | `USER` \| `BOT` |
| content | TEXT | |
| like_count | INT | default 0 |
| created_at | TIMESTAMP | auto |

### `comments`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| post_id | BIGINT FK → posts.id | |
| author_id | BIGINT | FK → users.id **or** bots.id |
| author_type | VARCHAR(10) | `USER` \| `BOT` |
| content | TEXT | |
| depth_level | INT | 1 = root comment |
| created_at | TIMESTAMP | auto |

---

## Phase 2 — Redis Virality Engine & Atomic Locks

### Virality Score Keys
```
post:{postId}:virality_score   → running total (INCR'd on every interaction)
```

| Event | Points |
|-------|--------|
| Bot Reply | +1 |
| Human Like | +20 |
| Human Comment | +50 |

### Guardrail Redis Keys
```
post:{postId}:bot_count              → atomic INCR counter (cap = 100)
cooldown:bot_{id}:human_{id}         → TTL key, 10 minutes
notif:cooldown:user_{id}             → TTL key, 15 minutes  
user:{id}:pending_notifs             → Redis List of queued notifications
```

---

## Phase 3 — Notification Engine

**When a bot interacts with a human's post:**

```
Is notif:cooldown:user_{id} set?
   YES → RPUSH user:{id}:pending_notifs "Bot X replied to your post #Y"
   NO  → log "PUSH NOTIFICATION SENT" + SET notif:cooldown:user_{id} EX 900 (15 min)
```

**CRON Sweeper** runs every 5 minutes (`0 0/5 * * * *`):
1. SCAN all `user:*:pending_notifs` keys
2. For each key → LRANGE + DELETE
3. Log: `"Summarized Push Notification: BotName and [N] others interacted with your posts."`

---

## Thread Safety — How Atomic Locks Work

This is the most important section of the assignment.

### Problem
Under 200 simultaneous requests, a naive implementation using a Java `synchronized` block or an in-memory counter would either:
- Fail under distributed deployments (state lives in one JVM, not shared)
- Allow race conditions where two threads both read "99", both decide they're allowed, and both insert → total becomes 101

### Solution: Redis as the Atomic Gatekeeper

#### 1. Horizontal Cap (≤ 100 bot replies per post)

```java
// GuardrailService.checkAndIncrementBotCount()
Long newCount = redisTemplate.opsForValue().increment(key);  // INCR is atomic
if (newCount > 100) {
    redisTemplate.opsForValue().decrement(key);  // compensate
    throw new GuardrailException("429 Too Many Requests");
}
```

**Why this is safe:**  
Redis `INCR` is a **single-server atomic operation**. Redis processes commands sequentially in its event loop — no two `INCR` calls on the same key can execute simultaneously. This means even if 200 threads call `INCR` at the exact same millisecond, they will each see a strictly unique sequential value (1, 2, 3 … 100, 101 …). Thread #101 gets value `101`, detects the cap, decrements back to `100`, and throws 429. **The DB insert only happens after a successful INCR ≤ 100**, so the database can never have 101 bot comments.

#### 2. Cooldown Cap (once per 10 min per bot–human pair)

```java
// SET key "1" NX EX 600  — atomic set-if-not-exists + TTL in one command
Boolean wasAbsent = redisTemplate.opsForValue()
        .setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
if (!wasAbsent) throw new GuardrailException("429 Cooldown active");
```

**Why this is safe:**  
`SET NX EX` is **one atomic Redis command** — there is no gap between the existence check and the set. Even with 1000 concurrent requests for the same bot–human pair, exactly one wins the race and sets the key. All others find the key present and are rejected.

#### 3. Statelessness
- **No** `HashMap`, **no** `static` fields, **no** instance variables hold state.
- All counters, cooldowns, and pending queues live exclusively in Redis.
- The application can be horizontally scaled (multiple pods) without coordination — Redis is the single source of truth for all guardrail state.

#### 4. DB–Redis Consistency
```
[Redis INCR] → success → [DB INSERT] → success ✓
[Redis INCR] → success → [DB INSERT] → failure → [Redis DECR] (compensate)
[Redis INCR] → fail (>100) → 429 returned, NO DB insert
```
The Redis gatekeeper runs **before** the DB transaction. If the DB insert fails after Redis was already updated, a compensating decrement restores the Redis counter to its pre-request value.

---

## Running the Concurrency Test

```bash
# Fire 200 concurrent bot comment requests at post 1
for i in {1..200}; do
  curl -s -X POST http://localhost:8080/api/posts/1/comments \
    -H "Content-Type: application/json" \
    -d '{"authorId":1,"authorType":"BOT","content":"Bot spam #'$i'","depthLevel":1}' &
done
wait

# Check Redis bot count
docker exec social_redis redis-cli GET post:1:bot_count

# Should be exactly 100 — no more
```

Expected result: exactly 100 comments in PostgreSQL, Redis counter = `100`, 100 requests get `429 Too Many Requests`.
