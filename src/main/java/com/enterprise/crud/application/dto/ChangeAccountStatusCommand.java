package com.enterprise.crud.application.dto;

import com.enterprise.crud.domain.model.AccountStatus;

import java.util.Objects;
import java.util.UUID;

public record ChangeAccountStatusCommand(UUID accountId, AccountStatus targetStatus) {

    public ChangeAccountStatusCommand {
        Objects.requireNonNull(targetStatus, "targetStatus must not be null");
    }
}
