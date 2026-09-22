package com.enterprise.crud.domain.exception;

import com.enterprise.crud.domain.model.DocumentNumber;

public final class DocumentNumberAlreadyRegisteredException extends DomainException {

    public DocumentNumberAlreadyRegisteredException(DocumentNumber documentNumber) {
        super("An account is already registered for document number: " + documentNumber);
    }
}
