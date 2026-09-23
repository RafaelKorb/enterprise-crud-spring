package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.AccountResult;
import com.enterprise.crud.application.dto.CreditAccountCommand;
import com.enterprise.crud.application.dto.DebitAccountCommand;
import com.enterprise.crud.application.dto.OpenAccountCommand;
import com.enterprise.crud.domain.exception.AccountNotFoundException;
import com.enterprise.crud.domain.exception.InsufficientBalanceException;
import com.enterprise.crud.domain.exception.InvalidAmountException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoveFundsAccountUseCaseTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
    private final CreditAccountUseCase credit = new CreditAccountService(repository);
    private final DebitAccountUseCase debit = new DebitAccountService(repository);
    private UUID accountId;

    @BeforeEach
    void openAccount() {
        accountId = new OpenAccountService(repository).execute(new OpenAccountCommand("52998224725")).id();
    }

    @Test
    void creditThenDebitPersistsBalanceAndLetsPersistenceBumpVersion() {
        credit.execute(new CreditAccountCommand(accountId, new BigDecimal("100.00")));
        AccountResult result = debit.execute(new DebitAccountCommand(accountId, new BigDecimal("30.00")));

        assertEquals(new BigDecimal("70.00"), result.balance());
        assertEquals(2L, result.version());
    }

    @Test
    void debitBeyondBalanceIsRejectedAndNothingIsSaved() {
        int savesBefore = repository.saveCount();

        assertThrows(InsufficientBalanceException.class,
                () -> debit.execute(new DebitAccountCommand(accountId, new BigDecimal("0.01"))));
        assertEquals(savesBefore, repository.saveCount());
    }

    @Test
    void invalidAmountIsRejected() {
        assertThrows(InvalidAmountException.class,
                () -> credit.execute(new CreditAccountCommand(accountId, BigDecimal.ZERO)));
        assertThrows(InvalidAmountException.class, () -> debit.execute(new DebitAccountCommand(accountId, null)));
    }

    @Test
    void unknownAccountIsReportedAsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThrows(AccountNotFoundException.class,
                () -> credit.execute(new CreditAccountCommand(unknown, BigDecimal.TEN)));
        assertThrows(AccountNotFoundException.class,
                () -> debit.execute(new DebitAccountCommand(unknown, BigDecimal.TEN)));
    }
}
