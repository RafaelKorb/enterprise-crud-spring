package com.enterprise.crud.infrastructure.persistence.repository;

import com.enterprise.crud.TestcontainersConfiguration;
import com.enterprise.crud.domain.exception.DocumentNumberAlreadyRegisteredException;
import com.enterprise.crud.domain.model.Account;
import com.enterprise.crud.domain.model.AccountId;
import com.enterprise.crud.domain.model.AccountStatus;
import com.enterprise.crud.domain.model.DocumentNumber;
import com.enterprise.crud.domain.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountRepositoryAdapterIT {

    private static final DocumentNumber DOCUMENT_NUMBER = new DocumentNumber("52998224725");

    @Autowired
    private AccountRepository repository;

    @Autowired
    private SpringDataAccountRepository jpaRepository;

    @BeforeEach
    void cleanDatabase() {
        jpaRepository.deleteAllInBatch();
    }

    @Test
    void persistsNewAccountAndReadsItBack() {
        Account saved = repository.save(Account.open(DOCUMENT_NUMBER));

        Account loaded = repository.findById(saved.id()).orElseThrow();
        assertEquals(0L, loaded.version());
        assertEquals(DOCUMENT_NUMBER, loaded.documentNumber());
        assertEquals(AccountStatus.ACTIVE, loaded.status());
        assertEquals(0, BigDecimal.ZERO.compareTo(loaded.balance()));
        assertTrue(repository.existsByDocumentNumber(DOCUMENT_NUMBER));
        assertEquals(saved.id(), repository.findByDocumentNumber(DOCUMENT_NUMBER).orElseThrow().id());
    }

    @Test
    void updateIsIncrementedByPersistence() {
        Account account = repository.save(Account.open(DOCUMENT_NUMBER));

        account.credit(new BigDecimal("150.25"));
        Account updated = repository.save(account);

        assertEquals(1L, updated.version());
        assertEquals(new BigDecimal("150.25"), repository.findById(account.id()).orElseThrow().balance());
    }

    @Test
    void staleWriteIsRejectedAndDoesNotOverwriteTheWinner() {
        AccountId id = repository.save(Account.open(DOCUMENT_NUMBER)).id();
        Account first = repository.findById(id).orElseThrow();
        Account second = repository.findById(id).orElseThrow();

        first.credit(new BigDecimal("10.00"));
        repository.save(first);
        second.credit(new BigDecimal("99.00"));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> repository.save(second));
        Account stored = repository.findById(id).orElseThrow();
        assertEquals(new BigDecimal("10.00"), stored.balance());
        assertEquals(1L, stored.version());
    }

    @Test
    void duplicateDocumentNumberIsTranslatedToDomainException() {
        repository.save(Account.open(DOCUMENT_NUMBER));

        assertThrows(DocumentNumberAlreadyRegisteredException.class,
                () -> repository.save(Account.open(DOCUMENT_NUMBER)));
        assertEquals(1, jpaRepository.count());
    }

    @Test
    void keysetPagesFollowDatabaseUuidOrderWithoutGapsOrDuplicates() {
        IntStream.range(0, 25).forEach(i ->
                repository.save(Account.open(new DocumentNumber("%011d".formatted(10_000_000_000L + i)))));

        List<AccountId> walked = new ArrayList<>();
        Optional<AccountId> cursor = Optional.empty();
        List<Account> page;
        do {
            page = repository.findPage(cursor, 10);
            page.forEach(account -> walked.add(account.id()));
            cursor = page.isEmpty() ? Optional.empty() : Optional.of(page.getLast().id());
        } while (page.size() == 10);

        List<AccountId> expected = jpaRepository.findAll().stream()
                .map(entity -> new AccountId(entity.getId()))
                .sorted((a, b) -> a.toString().compareTo(b.toString()))
                .toList();
        assertEquals(expected, walked);
    }

    @Test
    void unknownIdAndDocumentAreEmpty() {
        assertTrue(repository.findById(AccountId.newId()).isEmpty());
        assertFalse(repository.existsByDocumentNumber(DOCUMENT_NUMBER));
    }
}
