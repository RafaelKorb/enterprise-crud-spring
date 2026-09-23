package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.AccountPageResult;
import com.enterprise.crud.application.dto.AccountResult;
import com.enterprise.crud.application.dto.ListAccountsQuery;
import com.enterprise.crud.domain.model.Account;
import com.enterprise.crud.domain.model.AccountId;
import com.enterprise.crud.domain.repository.AccountRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ListAccountsService implements ListAccountsUseCase {

    private final AccountRepository accountRepository;

    public ListAccountsService(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
    }

    /**
     * Fetches one extra row to learn whether a next page exists without issuing a COUNT query.
     */
    @Override
    public AccountPageResult execute(ListAccountsQuery query) {
        List<Account> fetched = accountRepository.findPage(query.cursor().map(AccountId::new), query.limit() + 1);
        boolean hasNext = fetched.size() > query.limit();
        List<Account> page = hasNext ? fetched.subList(0, query.limit()) : fetched;
        Optional<UUID> nextCursor = hasNext ? Optional.of(page.getLast().id().value()) : Optional.empty();
        return new AccountPageResult(page.stream().map(AccountResult::from).toList(), nextCursor);
    }
}
