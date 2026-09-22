package com.enterprise.crud.infrastructure.persistence.mapper;

import com.enterprise.crud.domain.model.Account;
import com.enterprise.crud.domain.model.AccountId;
import com.enterprise.crud.domain.model.DocumentNumber;
import com.enterprise.crud.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Hand-written on purpose: the aggregate exposes fluent accessors ({@code id()}) and is rebuilt through
 * {@link Account#reconstitute}, neither of which MapStruct can map without per-field expressions.
 */
@Component
public class AccountPersistenceMapper {

    public Account toDomain(AccountJpaEntity entity) {
        return Account.reconstitute(new AccountId(entity.getId()), new DocumentNumber(entity.getDocumentNumber()),
                entity.getBalance(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getVersion());
    }

    public AccountJpaEntity toNewEntity(Account account) {
        return new AccountJpaEntity(account.id().value(), account.documentNumber().value(), account.balance(),
                account.status(), account.createdAt(), account.updatedAt());
    }

    /** Copies only the mutable state; identity, document and version are never overwritten. */
    public void copyMutableState(Account account, AccountJpaEntity entity) {
        entity.setBalance(account.balance());
        entity.setStatus(account.status());
        entity.setUpdatedAt(account.updatedAt());
    }
}
