package com.enterprise.crud.application.dto;

import java.util.Optional;
import java.util.UUID;

/**
 * Keyset page request: {@code cursor} is the id of the last account of the previous page, or empty for the first
 * page.
 */
public record ListAccountsQuery(Optional<UUID> cursor, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public ListAccountsQuery {
        cursor = cursor == null ? Optional.empty() : cursor;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and %d, got %d".formatted(MAX_LIMIT, limit));
        }
    }

    public static ListAccountsQuery firstPage() {
        return new ListAccountsQuery(Optional.empty(), DEFAULT_LIMIT);
    }
}
