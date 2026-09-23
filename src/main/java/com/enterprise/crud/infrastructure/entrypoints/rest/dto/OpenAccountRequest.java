package com.enterprise.crud.infrastructure.entrypoints.rest.dto;

import jakarta.validation.constraints.NotBlank;

/** Format rules (digits, CPF/CNPJ length) belong to the domain and surface as 422. */
public record OpenAccountRequest(@NotBlank String documentNumber) {
}
