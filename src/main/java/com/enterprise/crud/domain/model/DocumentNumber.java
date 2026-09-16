package com.enterprise.crud.domain.model;

import com.enterprise.crud.domain.exception.InvalidDocumentNumberException;

public record DocumentNumber(String value) {

    private static final int CPF_LENGTH = 11;
    private static final int CNPJ_LENGTH = 14;

    public DocumentNumber {
        if (value == null || value.isBlank()) {
            throw new InvalidDocumentNumberException("Document number must not be blank");
        }
        if (!value.chars().allMatch(Character::isDigit)) {
            throw new InvalidDocumentNumberException("Document number must contain only digits: " + value);
        }
        if (value.length() != CPF_LENGTH && value.length() != CNPJ_LENGTH) {
            throw new InvalidDocumentNumberException(
                    "Document number must have %d (CPF) or %d (CNPJ) digits, got %d"
                            .formatted(CPF_LENGTH, CNPJ_LENGTH, value.length()));
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
