package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.ChangeAccountStatusCommand;
import com.enterprise.crud.application.dto.CreditAccountCommand;
import com.enterprise.crud.application.dto.OpenAccountCommand;
import com.enterprise.crud.domain.exception.AccountNotFoundException;
import com.enterprise.crud.domain.exception.InsufficientBalanceException;
import com.enterprise.crud.domain.exception.InvalidAccountStatusTransitionException;
import com.enterprise.crud.domain.model.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeAccountStatusUseCaseTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
    private final ChangeAccountStatusUseCase useCase = new ChangeAccountStatusService(repository);
    private UUID accountId;

    @BeforeEach
    void openAccount() {
        accountId = new OpenAccountService(repository).execute(new OpenAccountCommand("52998224725")).id();
    }

    @Test
    void blocksAndReactivates() {
        assertEquals(AccountStatus.BLOCKED,
                useCase.execute(new ChangeAccountStatusCommand(accountId, AccountStatus.BLOCKED)).status());
        assertEquals(AccountStatus.ACTIVE,
                useCase.execute(new ChangeAccountStatusCommand(accountId, AccountStatus.ACTIVE)).status());
    }

    @Test
    void closesAccountWithZeroBalanceAndKeepsItClosed() {
        useCase.execute(new ChangeAccountStatusCommand(accountId, AccountStatus.CLOSED));

        assertThrows(InvalidAccountStatusTransitionException.class,
                () -> useCase.execute(new ChangeAccountStatusCommand(accountId, AccountStatus.ACTIVE)));
    }

    @Test
    void refusesToCloseAccountWithBalance() {
        new CreditAccountService(repository).execute(new CreditAccountCommand(accountId, BigDecimal.TEN));

        assertThrows(InsufficientBalanceException.class,
                () -> useCase.execute(new ChangeAccountStatusCommand(accountId, AccountStatus.CLOSED)));
    }

    @Test
    void unknownAccountIsReportedAsNotFound() {
        assertThrows(AccountNotFoundException.class,
                () -> useCase.execute(new ChangeAccountStatusCommand(UUID.randomUUID(), AccountStatus.BLOCKED)));
    }
}
