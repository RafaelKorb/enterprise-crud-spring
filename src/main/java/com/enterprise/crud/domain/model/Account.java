package com.enterprise.crud.domain.model;

import com.enterprise.crud.domain.exception.InsufficientBalanceException;
import com.enterprise.crud.domain.exception.InvalidAccountStatusTransitionException;
import com.enterprise.crud.domain.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class Account {

    /** Monetary amounts are stored with cent precision; finer values would be silently rounded. */
    public static final int MONEY_SCALE = 2;

    private final AccountId id;
    private final DocumentNumber documentNumber;
    private final Instant createdAt;
    private BigDecimal balance;
    private AccountStatus status;
    private Instant updatedAt;
    private long version;

    private Account(AccountId id, DocumentNumber documentNumber, BigDecimal balance, AccountStatus status,
            Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.documentNumber = documentNumber;
        this.balance = balance;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Account open(DocumentNumber documentNumber) {
        Objects.requireNonNull(documentNumber, "documentNumber must not be null");
        Instant now = Instant.now();
        return new Account(AccountId.newId(), documentNumber, BigDecimal.ZERO, AccountStatus.ACTIVE, now, now, 0L);
    }

    public static Account reconstitute(AccountId id, DocumentNumber documentNumber, BigDecimal balance,
            AccountStatus status, Instant createdAt, Instant updatedAt, long version) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(documentNumber, "documentNumber must not be null");
        Objects.requireNonNull(balance, "balance must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException("Account balance cannot be negative: " + balance);
        }
        return new Account(id, documentNumber, balance, status, createdAt, updatedAt, version);
    }

    public void credit(BigDecimal amount) {
        requirePositiveAmount(amount);
        requireActive();
        this.balance = this.balance.add(amount);
        touch();
    }

    public void debit(BigDecimal amount) {
        requirePositiveAmount(amount);
        requireActive();
        BigDecimal resultingBalance = this.balance.subtract(amount);
        if (resultingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance: available=%s, requested=%s".formatted(this.balance, amount));
        }
        this.balance = resultingBalance;
        touch();
    }

    public void activate() {
        changeStatus(AccountStatus.ACTIVE);
    }

    public void block() {
        changeStatus(AccountStatus.BLOCKED);
    }

    public void close() {
        if (this.balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new InsufficientBalanceException(
                    "Account cannot be closed with a non-zero balance: " + this.balance);
        }
        changeStatus(AccountStatus.CLOSED);
    }

    private void changeStatus(AccountStatus target) {
        Objects.requireNonNull(target, "target status must not be null");
        if (this.status == target) {
            return;
        }
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidAccountStatusTransitionException(
                    "Cannot transition account from %s to %s".formatted(this.status, target));
        }
        this.status = target;
        touch();
    }

    private void requireActive() {
        if (this.status != AccountStatus.ACTIVE) {
            throw new InvalidAccountStatusTransitionException(
                    "Account must be ACTIVE to perform this operation, current status: " + this.status);
        }
    }

    private static void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero: " + amount);
        }
        if (amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new InvalidAmountException(
                    "Amount must have at most %d decimal places: %s".formatted(MONEY_SCALE, amount));
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public AccountId id() {
        return id;
    }

    public DocumentNumber documentNumber() {
        return documentNumber;
    }

    public BigDecimal balance() {
        return balance;
    }

    public AccountStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Version read from storage, used for optimistic locking. The domain never increments it: the persistence
     * adapter owns increments, so a concurrent write is detected against the value that was loaded.
     */
    public long version() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
