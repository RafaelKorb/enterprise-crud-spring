package com.enterprise.crud.infrastructure.entrypoints.rest;

import com.enterprise.crud.application.dto.AccountResult;
import com.enterprise.crud.application.dto.GetAccountQuery;
import com.enterprise.crud.application.dto.ListAccountsQuery;
import com.enterprise.crud.application.usecase.ChangeAccountStatusUseCase;
import com.enterprise.crud.application.usecase.CreditAccountUseCase;
import com.enterprise.crud.application.usecase.DebitAccountUseCase;
import com.enterprise.crud.application.usecase.GetAccountUseCase;
import com.enterprise.crud.application.usecase.ListAccountsUseCase;
import com.enterprise.crud.application.usecase.OpenAccountUseCase;
import com.enterprise.crud.infrastructure.entrypoints.rest.dto.AccountPageResponse;
import com.enterprise.crud.infrastructure.entrypoints.rest.dto.AccountResponse;
import com.enterprise.crud.infrastructure.entrypoints.rest.dto.AmountRequest;
import com.enterprise.crud.infrastructure.entrypoints.rest.dto.ChangeAccountStatusRequest;
import com.enterprise.crud.infrastructure.entrypoints.rest.dto.OpenAccountRequest;
import com.enterprise.crud.infrastructure.entrypoints.rest.mapper.AccountRestMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
class AccountController {

    private final OpenAccountUseCase openAccount;
    private final CreditAccountUseCase creditAccount;
    private final DebitAccountUseCase debitAccount;
    private final ChangeAccountStatusUseCase changeAccountStatus;
    private final GetAccountUseCase getAccount;
    private final ListAccountsUseCase listAccounts;
    private final AccountRestMapper mapper;

    AccountController(OpenAccountUseCase openAccount, CreditAccountUseCase creditAccount,
            DebitAccountUseCase debitAccount, ChangeAccountStatusUseCase changeAccountStatus,
            GetAccountUseCase getAccount, ListAccountsUseCase listAccounts, AccountRestMapper mapper) {
        this.openAccount = openAccount;
        this.creditAccount = creditAccount;
        this.debitAccount = debitAccount;
        this.changeAccountStatus = changeAccountStatus;
        this.getAccount = getAccount;
        this.listAccounts = listAccounts;
        this.mapper = mapper;
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        AccountResult result = openAccount.execute(mapper.toCommand(request));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(result.id())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(result));
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable UUID id) {
        return mapper.toResponse(getAccount.execute(new GetAccountQuery(id)));
    }

    @GetMapping
    AccountPageResponse list(@RequestParam Optional<UUID> cursor,
            @RequestParam(defaultValue = "" + ListAccountsQuery.DEFAULT_LIMIT)
            @Min(1) @Max(ListAccountsQuery.MAX_LIMIT) int limit) {
        return mapper.toResponse(listAccounts.execute(new ListAccountsQuery(cursor, limit)));
    }

    @PostMapping("/{id}/credits")
    AccountResponse credit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return mapper.toResponse(creditAccount.execute(mapper.toCreditCommand(id, request)));
    }

    @PostMapping("/{id}/debits")
    AccountResponse debit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return mapper.toResponse(debitAccount.execute(mapper.toDebitCommand(id, request)));
    }

    /** PUT because moving to the current status is a no-op in the domain, which makes the call idempotent. */
    @PutMapping("/{id}/status")
    AccountResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeAccountStatusRequest request) {
        return mapper.toResponse(changeAccountStatus.execute(mapper.toCommand(id, request)));
    }
}
