package com.enterprise.crud.infrastructure.entrypoints.rest.handler;

import com.enterprise.crud.domain.exception.AccountNotFoundException;
import com.enterprise.crud.domain.exception.DocumentNumberAlreadyRegisteredException;
import com.enterprise.crud.domain.exception.DomainException;
import com.enterprise.crud.domain.exception.InvalidAccountStatusTransitionException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates failures into RFC 9457 problem details (application/problem+json). Spring MVC errors (malformed JSON,
 * bean validation, bad path/query types) are handled by the superclass as 400.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        HttpStatus status = switch (ex) {
            case AccountNotFoundException _ -> HttpStatus.NOT_FOUND;
            case DocumentNumberAlreadyRegisteredException _, InvalidAccountStatusTransitionException _ ->
                    HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    }

    /** The detail is generic on purpose: the client only needs to reload the account and retry. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The account was modified concurrently; reload it and retry");
    }
}
