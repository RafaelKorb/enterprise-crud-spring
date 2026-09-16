package com.enterprise.crud.domain.repository;

import com.enterprise.crud.domain.model.Account;
import com.enterprise.crud.domain.model.AccountId;
import com.enterprise.crud.domain.model.DocumentNumber;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(AccountId id);

    Optional<Account> findByDocumentNumber(DocumentNumber documentNumber);

    boolean existsByDocumentNumber(DocumentNumber documentNumber);

    /**
     * Keyset pagination ordered by id: returns up to {@code limit} accounts whose id comes strictly
     * after {@code cursor}, or the first page when {@code cursor} is empty.
     */
    List<Account> findPage(Optional<AccountId> cursor, int limit);
}
