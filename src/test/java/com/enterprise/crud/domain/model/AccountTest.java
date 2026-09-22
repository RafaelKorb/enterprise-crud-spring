package com.enterprise.crud.domain.model;

import com.enterprise.crud.domain.exception.InsufficientBalanceException;
import com.enterprise.crud.domain.exception.InvalidAccountStatusTransitionException;
import com.enterprise.crud.domain.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTest {

    private static final DocumentNumber DOCUMENT_NUMBER = new DocumentNumber("52998224725");

    @Test
    void opensWithZeroBalanceActiveStatusAndVersionZero() {
        Account account = Account.open(DOCUMENT_NUMBER);

        assertEquals(BigDecimal.ZERO, account.balance());
        assertEquals(AccountStatus.ACTIVE, account.status());
        assertEquals(0L, account.version());
        assertEquals(DOCUMENT_NUMBER, account.documentNumber());
    }

    @Test
    void creditIncreasesBalance() {
        Account account = Account.open(DOCUMENT_NUMBER);

        account.credit(new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"), account.balance());
    }

    @Test
    void creditRejectsZeroOrNegativeAmount() {
        Account account = Account.open(DOCUMENT_NUMBER);

        assertThrows(InvalidAmountException.class, () -> account.credit(BigDecimal.ZERO));
        assertThrows(InvalidAmountException.class, () -> account.credit(new BigDecimal("-10.00")));
        assertEquals(BigDecimal.ZERO, account.balance(), "rejected mutations must not change the balance");
    }

    @Test
    void debitDecreasesBalance() {
        Account account = Account.open(DOCUMENT_NUMBER);
        account.credit(new BigDecimal("100.00"));

        account.debit(new BigDecimal("40.00"));

        assertEquals(new BigDecimal("60.00"), account.balance());
    }

    @Test
    void debitNeverAllowsBalanceToGoNegative() {
        Account account = Account.open(DOCUMENT_NUMBER);
        account.credit(new BigDecimal("50.00"));

        InsufficientBalanceException exception = assertThrows(InsufficientBalanceException.class,
                () -> account.debit(new BigDecimal("50.01")));

        assertTrue(exception.getMessage().contains("Insufficient balance"));
        assertEquals(new BigDecimal("50.00"), account.balance(), "balance must remain unchanged after rejection");
    }

    @Test
    void debitRejectsZeroOrNegativeAmount() {
        Account account = Account.open(DOCUMENT_NUMBER);

        assertThrows(InvalidAmountException.class, () -> account.debit(BigDecimal.ZERO));
        assertThrows(InvalidAmountException.class, () -> account.debit(new BigDecimal("-1.00")));
    }

    @Test
    void blockedAccountRejectsCreditAndDebit() {
        Account account = Account.open(DOCUMENT_NUMBER);
        account.block();

        assertThrows(InvalidAccountStatusTransitionException.class, () -> account.credit(new BigDecimal("10.00")));
        assertThrows(InvalidAccountStatusTransitionException.class, () -> account.debit(new BigDecimal("10.00")));
    }

    @Test
    void blockThenActivateRoundTripsStatus() {
        Account account = Account.open(DOCUMENT_NUMBER);

        account.block();
        assertEquals(AccountStatus.BLOCKED, account.status());

        account.activate();
        assertEquals(AccountStatus.ACTIVE, account.status());
    }

    @Test
    void mutationsKeepTheLoadedVersionBecausePersistenceOwnsIncrements() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Account account = Account.reconstitute(AccountId.newId(), DOCUMENT_NUMBER, new BigDecimal("100.00"),
                AccountStatus.ACTIVE, createdAt, createdAt, 7L);

        account.credit(new BigDecimal("10.00"));
        account.debit(new BigDecimal("5.00"));
        account.block();

        assertEquals(7L, account.version());
        assertTrue(account.updatedAt().isAfter(createdAt), "mutations must refresh updatedAt");
    }

    @Test
    void closingAccountWithNonZeroBalanceIsRejected() {
        Account account = Account.open(DOCUMENT_NUMBER);
        account.credit(new BigDecimal("10.00"));

        assertThrows(InsufficientBalanceException.class, account::close);
        assertEquals(AccountStatus.ACTIVE, account.status(), "status must not change when close is rejected");
    }

    @Test
    void closingAccountWithZeroBalanceSucceeds() {
        Account account = Account.open(DOCUMENT_NUMBER);

        account.close();

        assertEquals(AccountStatus.CLOSED, account.status());
    }

    @Test
    void closedAccountCannotTransitionToAnyOtherStatus() {
        Account account = Account.open(DOCUMENT_NUMBER);
        account.close();

        assertThrows(InvalidAccountStatusTransitionException.class, account::activate);
        assertThrows(InvalidAccountStatusTransitionException.class, account::block);
    }

    @Test
    void redundantStatusTransitionIsIdempotentAndDoesNotTouchUpdatedAt() {
        Account account = Account.open(DOCUMENT_NUMBER);
        Instant updatedAt = account.updatedAt();

        account.activate();

        assertEquals(AccountStatus.ACTIVE, account.status());
        assertEquals(updatedAt, account.updatedAt());
    }

    @Test
    void reconstituteRejectsNegativeBalance() {
        assertThrows(InsufficientBalanceException.class, () -> Account.reconstitute(
                AccountId.newId(), DOCUMENT_NUMBER, new BigDecimal("-0.01"), AccountStatus.ACTIVE,
                Instant.now(), Instant.now(), 0L));
    }

    @Test
    void reconstituteRebuildsAnExistingAccountWithoutResettingState() {
        AccountId id = AccountId.newId();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-02T00:00:00Z");

        Account account = Account.reconstitute(id, DOCUMENT_NUMBER, new BigDecimal("250.00"), AccountStatus.BLOCKED,
                createdAt, updatedAt, 7L);

        assertEquals(id, account.id());
        assertEquals(new BigDecimal("250.00"), account.balance());
        assertEquals(AccountStatus.BLOCKED, account.status());
        assertEquals(createdAt, account.createdAt());
        assertEquals(updatedAt, account.updatedAt());
        assertEquals(7L, account.version());
    }

    @Test
    void accountsAreEqualWhenIdsAreEqualRegardlessOfState() {
        AccountId id = AccountId.newId();
        Instant now = Instant.now();
        Account first = Account.reconstitute(id, DOCUMENT_NUMBER, BigDecimal.ZERO, AccountStatus.ACTIVE, now, now, 0L);
        Account second = Account.reconstitute(id, DOCUMENT_NUMBER, new BigDecimal("999.00"), AccountStatus.BLOCKED,
                now, now, 5L);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
