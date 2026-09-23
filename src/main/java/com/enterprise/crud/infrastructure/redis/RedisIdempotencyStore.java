package com.enterprise.crud.infrastructure.redis;

import com.enterprise.crud.infrastructure.entrypoints.rest.idempotency.IdempotencyRecord;
import com.enterprise.crud.infrastructure.entrypoints.rest.idempotency.IdempotencyStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * Keeps idempotency records as JSON strings. The claim relies on {@code SET key value NX EX ttl}, which is atomic
 * in Redis, so two instances cannot both process the same key.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final int MAX_RESERVE_ATTEMPTS = 3;

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public RedisIdempotencyStore(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    /** Retries when the existing record expires between the failed claim and the read. */
    @Override
    public Optional<IdempotencyRecord> reserve(String key, String fingerprint, Duration lockTtl) {
        String claim = jsonMapper.writeValueAsString(IdempotencyRecord.inProgress(fingerprint));
        for (int attempt = 0; attempt < MAX_RESERVE_ATTEMPTS; attempt++) {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, claim, lockTtl))) {
                return Optional.empty();
            }
            String stored = redis.opsForValue().get(key);
            if (stored != null) {
                return Optional.of(jsonMapper.readValue(stored, IdempotencyRecord.class));
            }
        }
        throw new IllegalStateException("Could not claim idempotency key " + key);
    }

    @Override
    public void complete(String key, IdempotencyRecord record, Duration retention) {
        redis.opsForValue().set(key, jsonMapper.writeValueAsString(record), retention);
    }

    @Override
    public void release(String key) {
        redis.delete(key);
    }
}
