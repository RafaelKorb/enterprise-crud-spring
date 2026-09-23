package com.enterprise.crud.infrastructure.redis;

import com.enterprise.crud.TestcontainersConfiguration;
import com.enterprise.crud.infrastructure.entrypoints.rest.idempotency.IdempotencyRecord;
import com.enterprise.crud.infrastructure.entrypoints.rest.idempotency.IdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RedisIdempotencyStoreIT {

    private static final String KEY = "idempotency:POST:/api/v1/accounts:k1";

    @Autowired
    private IdempotencyStore store;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void cleanRedis() {
        redis.delete(KEY);
    }

    @Test
    void firstReserveClaimsTheKeyAndLaterOnesSeeTheClaim() {
        assertTrue(store.reserve(KEY, "fp", Duration.ofSeconds(30)).isEmpty());

        Optional<IdempotencyRecord> existing = store.reserve(KEY, "fp", Duration.ofSeconds(30));

        assertTrue(existing.isPresent());
        assertEquals("fp", existing.get().fingerprint());
        assertFalse(existing.get().isCompleted());
        assertTrue(redis.getExpire(KEY) > 0 && redis.getExpire(KEY) <= 30);
    }

    @Test
    void completedRecordRoundTripsWithRetention() {
        store.reserve(KEY, "fp", Duration.ofSeconds(30));
        store.complete(KEY, IdempotencyRecord.completed("fp", 201, Map.of("Location", "/api/v1/accounts/1"),
                "{\"id\":\"1\"}"), Duration.ofHours(24));

        IdempotencyRecord stored = store.reserve(KEY, "fp", Duration.ofSeconds(30)).orElseThrow();

        assertEquals(IdempotencyRecord.completed("fp", 201, Map.of("Location", "/api/v1/accounts/1"),
                "{\"id\":\"1\"}"), stored);
        assertTrue(redis.getExpire(KEY) > Duration.ofHours(23).toSeconds());
    }

    @Test
    void releaseFreesTheKey() {
        store.reserve(KEY, "fp", Duration.ofSeconds(30));

        store.release(KEY);

        assertTrue(store.reserve(KEY, "fp", Duration.ofSeconds(30)).isEmpty());
    }

    @Test
    void concurrentReservesHaveExactlyOneWinner() throws Exception {
        Callable<Boolean> attempt = () -> store.reserve(KEY, "fp", Duration.ofSeconds(30)).isEmpty();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = IntStream.range(0, 20).mapToObj(_ -> executor.submit(attempt)).toList();
            long winners = 0;
            for (Future<Boolean> future : futures) {
                winners += future.get() ? 1 : 0;
            }
            assertEquals(1, winners);
        }
    }
}
