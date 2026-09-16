package com.enterprise.crud.domain.exception;

public final class InvalidAccountStatusTransitionException extends DomainException {

    public InvalidAccountStatusTransitionException(String message) {
        super(message);
    }
}
