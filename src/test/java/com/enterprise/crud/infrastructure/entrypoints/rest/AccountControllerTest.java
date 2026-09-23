package com.enterprise.crud.infrastructure.entrypoints.rest;

import com.enterprise.crud.application.dto.AccountPageResult;
import com.enterprise.crud.application.dto.AccountResult;
import com.enterprise.crud.application.dto.ChangeAccountStatusCommand;
import com.enterprise.crud.application.dto.CreditAccountCommand;
import com.enterprise.crud.application.dto.ListAccountsQuery;
import com.enterprise.crud.application.dto.OpenAccountCommand;
import com.enterprise.crud.application.usecase.ChangeAccountStatusUseCase;
import com.enterprise.crud.application.usecase.CreditAccountUseCase;
import com.enterprise.crud.application.usecase.DebitAccountUseCase;
import com.enterprise.crud.application.usecase.GetAccountUseCase;
import com.enterprise.crud.application.usecase.ListAccountsUseCase;
import com.enterprise.crud.application.usecase.OpenAccountUseCase;
import com.enterprise.crud.domain.exception.AccountNotFoundException;
import com.enterprise.crud.domain.exception.DocumentNumberAlreadyRegisteredException;
import com.enterprise.crud.domain.exception.InsufficientBalanceException;
import com.enterprise.crud.domain.exception.InvalidAccountStatusTransitionException;
import com.enterprise.crud.domain.exception.InvalidAmountException;
import com.enterprise.crud.domain.exception.InvalidDocumentNumberException;
import com.enterprise.crud.domain.model.AccountId;
import com.enterprise.crud.domain.model.AccountStatus;
import com.enterprise.crud.domain.model.DocumentNumber;
import com.enterprise.crud.infrastructure.entrypoints.rest.mapper.AccountRestMapperImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** HTTP contract: routing, request mapping, validation and the error-to-status table of the handler. */
@WebMvcTest(AccountController.class)
@Import(AccountRestMapperImpl.class)
class AccountControllerTest {

    private static final UUID ID = UUID.fromString("0198a3c0-0000-7000-8000-000000000001");
    private static final AccountResult ACCOUNT = new AccountResult(ID, "52998224725", new BigDecimal("10.50"),
            AccountStatus.ACTIVE, Instant.parse("2026-09-23T10:00:00Z"), Instant.parse("2026-09-23T10:05:00Z"), 1L);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private OpenAccountUseCase openAccount;
    @MockitoBean
    private CreditAccountUseCase creditAccount;
    @MockitoBean
    private DebitAccountUseCase debitAccount;
    @MockitoBean
    private ChangeAccountStatusUseCase changeAccountStatus;
    @MockitoBean
    private GetAccountUseCase getAccount;
    @MockitoBean
    private ListAccountsUseCase listAccounts;

    @Test
    void openReturnsCreatedWithLocationAndBody() {
        given(openAccount.execute(new OpenAccountCommand("52998224725"))).willReturn(ACCOUNT);

        assertThat(mvc.post().uri("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"documentNumber": "52998224725"}"""))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/v1/accounts/" + ID)
                .bodyJson()
                .hasPathSatisfying("$.id", id -> id.assertThat().isEqualTo(ID.toString()))
                .hasPathSatisfying("$.balance", balance -> balance.assertThat().isEqualTo(10.5))
                .hasPathSatisfying("$.status", status -> status.assertThat().isEqualTo("ACTIVE"))
                .hasPathSatisfying("$.createdAt", at -> at.assertThat().isEqualTo("2026-09-23T10:00:00Z"))
                .hasPathSatisfying("$.version", version -> version.assertThat().isEqualTo(1));
    }

    @Test
    void openWithBlankDocumentIsBadRequest() {
        assertThat(mvc.post().uri("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"documentNumber": " "}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        verifyNoInteractions(openAccount);
    }

    @Test
    void getReturnsAccount() {
        given(getAccount.execute(any())).willReturn(ACCOUNT);

        assertThat(mvc.get().uri("/api/v1/accounts/{id}", ID))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.documentNumber", doc -> doc.assertThat().isEqualTo("52998224725"));
    }

    @Test
    void unsupportedVersionIsRejected() {
        assertThat(mvc.get().uri("/api/v2/accounts/{id}", ID))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        verifyNoInteractions(getAccount);
    }

    @Test
    void malformedIdIsBadRequest() {
        assertThat(mvc.get().uri("/api/v1/accounts/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(getAccount);
    }

    @Test
    void listUsesDefaultLimitAndExposesNextCursor() {
        UUID next = UUID.randomUUID();
        given(listAccounts.execute(any())).willReturn(new AccountPageResult(List.of(ACCOUNT), Optional.of(next)));

        assertThat(mvc.get().uri("/api/v1/accounts"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.items[0].id", id -> id.assertThat().isEqualTo(ID.toString()))
                .hasPathSatisfying("$.nextCursor", cursor -> cursor.assertThat().isEqualTo(next.toString()));
        verify(listAccounts).execute(new ListAccountsQuery(Optional.empty(), ListAccountsQuery.DEFAULT_LIMIT));
    }

    @Test
    void listForwardsCursorAndLimit() {
        given(listAccounts.execute(any())).willReturn(new AccountPageResult(List.of(), Optional.empty()));

        assertThat(mvc.get().uri("/api/v1/accounts?cursor={cursor}&limit=5", ID))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.nextCursor", cursor -> cursor.assertThat().isNull());
        verify(listAccounts).execute(new ListAccountsQuery(Optional.of(ID), 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "abc"})
    void listRejectsOutOfRangeLimit(String limit) {
        assertThat(mvc.get().uri("/api/v1/accounts?limit={limit}", limit)).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(listAccounts);
    }

    @Test
    void creditMapsPathAndBodyIntoCommand() {
        given(creditAccount.execute(any())).willReturn(ACCOUNT);

        assertThat(mvc.post().uri("/api/v1/accounts/{id}/credits", ID).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": 10.50}"""))
                .hasStatusOk();

        ArgumentCaptor<CreditAccountCommand> command = ArgumentCaptor.forClass(CreditAccountCommand.class);
        verify(creditAccount).execute(command.capture());
        assertThat(command.getValue().accountId()).isEqualTo(ID);
        assertThat(command.getValue().amount()).isEqualByComparingTo("10.50");
    }

    @Test
    void debitWithoutAmountIsBadRequest() {
        assertThat(mvc.post().uri("/api/v1/accounts/{id}/debits", ID).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(debitAccount);
    }

    @Test
    void changeStatusMapsTargetStatus() {
        given(changeAccountStatus.execute(any())).willReturn(ACCOUNT);

        assertThat(mvc.put().uri("/api/v1/accounts/{id}/status", ID).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "BLOCKED"}"""))
                .hasStatusOk();
        verify(changeAccountStatus).execute(new ChangeAccountStatusCommand(ID, AccountStatus.BLOCKED));
    }

    @Test
    void unknownStatusIsBadRequest() {
        assertThat(mvc.put().uri("/api/v1/accounts/{id}/status", ID).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "FROZEN"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(changeAccountStatus);
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new AccountNotFoundException(new AccountId(ID)), HttpStatus.NOT_FOUND),
                Arguments.of(new DocumentNumberAlreadyRegisteredException(new DocumentNumber("52998224725")),
                        HttpStatus.CONFLICT),
                Arguments.of(new InvalidAccountStatusTransitionException("closed"), HttpStatus.CONFLICT),
                Arguments.of(new ObjectOptimisticLockingFailureException("Account", ID), HttpStatus.CONFLICT),
                Arguments.of(new InsufficientBalanceException("no funds"), HttpStatus.UNPROCESSABLE_CONTENT),
                Arguments.of(new InvalidAmountException("negative"), HttpStatus.UNPROCESSABLE_CONTENT),
                Arguments.of(new InvalidDocumentNumberException("letters"), HttpStatus.UNPROCESSABLE_CONTENT));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void failuresBecomeProblemDetails(RuntimeException failure, HttpStatus expected) {
        given(debitAccount.execute(any())).willThrow(failure);

        assertThat(mvc.post().uri("/api/v1/accounts/{id}/debits", ID).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": 1.00}"""))
                .hasStatus(expected)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.status", status -> status.assertThat().isEqualTo(expected.value()))
                .hasPathSatisfying("$.detail", detail -> detail.assertThat().isNotNull());
    }
}
