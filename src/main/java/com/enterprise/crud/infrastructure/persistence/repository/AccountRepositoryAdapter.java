package com.enterprise.crud.infrastructure.persistence.repository;

import com.enterprise.crud.domain.exception.DocumentNumberAlreadyRegisteredException;
import com.enterprise.crud.domain.model.Account;
import com.enterprise.crud.domain.model.AccountId;
import com.enterprise.crud.domain.model.DocumentNumber;
import com.enterprise.crud.domain.repository.AccountRepository;
import com.enterprise.crud.infrastructure.persistence.entity.AccountJpaEntity;
import com.enterprise.crud.infrastructure.persistence.mapper.AccountPersistenceMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class AccountRepositoryAdapter implements AccountRepository {

    private static final String DOCUMENT_NUMBER_CONSTRAINT = "uk_account_document_number";

    private final SpringDataAccountRepository jpaRepository;
    private final AccountPersistenceMapper mapper;

    public AccountRepositoryAdapter(SpringDataAccountRepository jpaRepository, AccountPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * The aggregate was loaded in an earlier transaction, so its version is compared with the row before copying
     * state; a write committed after this check is still caught by Hibernate's {@code WHERE version = ?}.
     */
    @Override
    @Transactional
    public Account save(Account account) {
        AccountJpaEntity entity = jpaRepository.findById(account.id().value()).orElse(null);
        if (entity == null) {
            entity = mapper.toNewEntity(account);
        } else {
            if (!Objects.equals(entity.getVersion(), account.version())) {
                throw new ObjectOptimisticLockingFailureException(AccountJpaEntity.class, account.id().value());
            }
            mapper.copyMutableState(account, entity);
        }
        try {
            return mapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            if (violates(e, DOCUMENT_NUMBER_CONSTRAINT)) {
                throw new DocumentNumberAlreadyRegisteredException(account.documentNumber());
            }
            throw e;
        }
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findByDocumentNumber(DocumentNumber documentNumber) {
        return jpaRepository.findByDocumentNumber(documentNumber.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByDocumentNumber(DocumentNumber documentNumber) {
        return jpaRepository.existsByDocumentNumber(documentNumber.value());
    }

    @Override
    public List<Account> findPage(Optional<AccountId> cursor, int limit) {
        List<AccountJpaEntity> rows = cursor
                .map(c -> jpaRepository.findByIdGreaterThanOrderByIdAsc(c.value(), Limit.of(limit)))
                .orElseGet(() -> jpaRepository.findAllByOrderByIdAsc(Limit.of(limit)));
        return rows.stream().map(mapper::toDomain).toList();
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
