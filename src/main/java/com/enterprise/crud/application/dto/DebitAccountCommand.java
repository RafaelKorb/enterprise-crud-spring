package com.enterprise.crud.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DebitAccountCommand(UUID accountId, BigDecimal amount) {
}
