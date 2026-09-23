package com.enterprise.crud.infrastructure.entrypoints.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Positivity and cent precision are domain rules and surface as 422. */
public record AmountRequest(@NotNull BigDecimal amount) {
}
