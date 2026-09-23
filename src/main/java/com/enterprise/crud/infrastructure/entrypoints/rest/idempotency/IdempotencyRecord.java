package com.enterprise.crud.infrastructure.entrypoints.rest.idempotency;

import java.util.Map;
import java.util.Objects;

/**
 * What is kept under an idempotency key: the request fingerprint and, once the request succeeded, the response to
 * replay. A record without {@code status} marks a request that is still being processed.
 */
public record IdempotencyRecord(String fingerprint, Integer status, Map<String, String> headers, String body) {

    public IdempotencyRecord {
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static IdempotencyRecord inProgress(String fingerprint) {
        return new IdempotencyRecord(fingerprint, null, Map.of(), null);
    }

    public static IdempotencyRecord completed(String fingerprint, int status, Map<String, String> headers,
            String body) {
        return new IdempotencyRecord(fingerprint, status, headers, body);
    }

    public boolean isCompleted() {
        return status != null;
    }
}
