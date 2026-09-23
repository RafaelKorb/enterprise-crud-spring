package com.enterprise.crud.infrastructure.entrypoints.rest.dto;

import com.enterprise.crud.domain.model.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeAccountStatusRequest(@NotNull AccountStatus status) {
}
