package com.enterprise.crud.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditAccountCommand(UUID accountId, BigDecimal amount) {
}
