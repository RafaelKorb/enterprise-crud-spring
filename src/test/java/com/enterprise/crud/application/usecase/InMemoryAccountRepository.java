package com.enterprise.crud.application.usecase;

import com.enterprise.crud.domain.model.Account;
import com.enterprise.crud.domain.model.AccountId;
import com.enterprise.crud.domain.model.DocumentNumber;
import com.enterprise.crud.domain.repository.AccountRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test double that behaves like the persistence adapter: it stores snapshots, so callers only see changes after
 * {@link #save}, and it owns version increments.
 */
final class InMemoryAccountRepository implements AccountRepository {

    // Hex string order matches PostgreSQL's unsigned byte-wise uuid ordering; UUID.compareTo is signed.
    private static final Comparator<Account> BY_ID = Comparator.comparing(account -> account.id().toString());

    private final Map<AccountId, Account> accounts = new ConcurrentHashMap<>();
    private int saveCount;

    @Override
    public Account save(Account account) {
        long nextVersion = accounts.containsKey(account.id()) ? account.version() + 1 : account.version();
        Account stored = copy(account, nextVersion);
        accounts.put(stored.id(), stored);
        saveCount++;
        return copy(stored, stored.version());
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return Optional.ofNullable(accounts.get(id)).map(account -> copy(account, account.version()));
    }

    @Override
    public Optional<Account> findByDocumentNumber(DocumentNumber documentNumber) {
        return accounts.values().stream()
                .filter(account -> account.documentNumber().equals(documentNumber))
                .findFirst()
                .map(account -> copy(account, account.version()));
    }

    @Override
    public boolean existsByDocumentNumber(DocumentNumber documentNumber) {
        return accounts.values().stream().anyMatch(account -> account.documentNumber().equals(documentNumber));
    }

    @Override
    public List<Account> findPage(Optional<AccountId> cursor, int limit) {
        return accounts.values().stream()
                .filter(account -> cursor.map(c -> account.id().toString().compareTo(c.toString()) > 0).orElse(true))
                .sorted(BY_ID)
                .limit(limit)
                .map(account -> copy(account, account.version()))
                .toList();
    }

    int saveCount() {
        return saveCount;
    }

    private static Account copy(Account account, long version) {
        return Account.reconstitute(account.id(), account.documentNumber(), account.balance(), account.status(),
                account.createdAt(), account.updatedAt(), version);
    }
}
