package com.enterprise.crud.infrastructure.entrypoints.rest.dto;

import com.enterprise.crud.domain.model.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String documentNumber, BigDecimal balance, AccountStatus status,
        Instant createdAt, Instant updatedAt, long version) {
}
