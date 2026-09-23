package com.enterprise.crud.infrastructure.entrypoints.rest.dto;

import java.util.List;
import java.util.UUID;

/** {@code nextCursor} is null on the last page; pass it back as {@code ?cursor=} to fetch the next one. */
public record AccountPageResponse(List<AccountResponse> items, UUID nextCursor) {
}
