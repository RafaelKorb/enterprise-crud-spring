package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.AccountResult;
import com.enterprise.crud.application.dto.OpenAccountCommand;
import com.enterprise.crud.domain.exception.DocumentNumberAlreadyRegisteredException;
import com.enterprise.crud.domain.exception.InvalidDocumentNumberException;
import com.enterprise.crud.domain.model.AccountStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAccountUseCaseTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
    private final OpenAccountUseCase useCase = new OpenAccountService(repository);

    @Test
    void opensActiveAccountWithZeroBalance() {
        AccountResult result = useCase.execute(new OpenAccountCommand("52998224725"));

        assertEquals("52998224725", result.documentNumber());
        assertEquals(BigDecimal.ZERO, result.balance());
        assertEquals(AccountStatus.ACTIVE, result.status());
        assertEquals(0L, result.version());
    }

    @Test
    void rejectsDocumentNumberAlreadyRegistered() {
        useCase.execute(new OpenAccountCommand("52998224725"));

        assertThrows(DocumentNumberAlreadyRegisteredException.class,
                () -> useCase.execute(new OpenAccountCommand("52998224725")));
        assertEquals(1, repository.saveCount());
    }

    @Test
    void rejectsInvalidDocumentNumberWithoutSaving() {
        assertThrows(InvalidDocumentNumberException.class, () -> useCase.execute(new OpenAccountCommand("123")));
        assertEquals(0, repository.saveCount());
    }
}
