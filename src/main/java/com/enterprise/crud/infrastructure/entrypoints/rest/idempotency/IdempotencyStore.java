package com.enterprise.crud.infrastructure.entrypoints.rest.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Shared storage for idempotency keys. Must be shared across instances (the application is stateless), so
 * implementations are backed by an external store. Failures surface as Spring {@code DataAccessException}s.
 */
public interface IdempotencyStore {

    /**
     * Atomically claims {@code key} with an in-progress record that expires after {@code lockTtl}.
     *
     * @return empty when the claim succeeded, otherwise the record already stored under the key
     */
    Optional<IdempotencyRecord> reserve(String key, String fingerprint, Duration lockTtl);

    /** Replaces the in-progress claim with the final response, kept for {@code retention}. */
    void complete(String key, IdempotencyRecord record, Duration retention);

    /** Drops the claim so the same key can be retried. */
    void release(String key);
}
