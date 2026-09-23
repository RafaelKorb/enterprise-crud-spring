package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.AccountPageResult;
import com.enterprise.crud.application.dto.AccountResult;
import com.enterprise.crud.application.dto.GetAccountQuery;
import com.enterprise.crud.application.dto.ListAccountsQuery;
import com.enterprise.crud.application.dto.OpenAccountCommand;
import com.enterprise.crud.domain.exception.AccountNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryAccountUseCaseTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
    private final OpenAccountUseCase open = new OpenAccountService(repository);
    private final GetAccountUseCase get = new GetAccountService(repository);
    private final ListAccountsUseCase list = new ListAccountsService(repository);

    @Test
    void getsAccountById() {
        AccountResult opened = open.execute(new OpenAccountCommand("52998224725"));

        assertEquals(opened, get.execute(new GetAccountQuery(opened.id())));
    }

    @Test
    void getReportsUnknownAccountAsNotFound() {
        assertThrows(AccountNotFoundException.class, () -> get.execute(new GetAccountQuery(UUID.randomUUID())));
    }

    @Test
    void walksAllPagesWithCursorWithoutGapsOrDuplicates() {
        IntStream.range(0, 7).forEach(i -> open.execute(new OpenAccountCommand("5299822472%d".formatted(i))));

        List<UUID> seen = new ArrayList<>();
        Optional<UUID> cursor = Optional.empty();
        int pages = 0;
        do {
            AccountPageResult page = list.execute(new ListAccountsQuery(cursor, 3));
            page.items().forEach(item -> seen.add(item.id()));
            cursor = page.nextCursor();
            pages++;
        } while (cursor.isPresent());

        assertEquals(3, pages);
        assertEquals(7, seen.size());
        assertEquals(7, new HashSet<>(seen).size());
    }

    @Test
    void lastFullPageHasNoNextCursor() {
        IntStream.range(0, 3).forEach(i -> open.execute(new OpenAccountCommand("5299822472%d".formatted(i))));

        AccountPageResult page = list.execute(new ListAccountsQuery(Optional.empty(), 3));

        assertEquals(3, page.items().size());
        assertTrue(page.nextCursor().isEmpty());
    }

    @Test
    void emptyRepositoryReturnsEmptyFirstPage() {
        AccountPageResult page = list.execute(ListAccountsQuery.firstPage());

        assertTrue(page.items().isEmpty());
        assertTrue(page.nextCursor().isEmpty());
    }

    @Test
    void rejectsLimitOutsideBounds() {
        assertThrows(IllegalArgumentException.class, () -> new ListAccountsQuery(Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ListAccountsQuery(Optional.empty(), ListAccountsQuery.MAX_LIMIT + 1));
    }
}
