package com.enterprise.crud.application.dto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One keyset page. {@code nextCursor} is present only when more accounts exist after this page.
 */
public record AccountPageResult(List<AccountResult> items, Optional<UUID> nextCursor) {

    public AccountPageResult {
        items = List.copyOf(items);
    }
}
