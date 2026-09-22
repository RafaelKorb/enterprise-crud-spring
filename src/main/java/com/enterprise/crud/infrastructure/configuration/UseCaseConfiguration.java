package com.enterprise.crud.infrastructure.configuration;

import com.enterprise.crud.application.usecase.ChangeAccountStatusService;
import com.enterprise.crud.application.usecase.ChangeAccountStatusUseCase;
import com.enterprise.crud.application.usecase.CreditAccountService;
import com.enterprise.crud.application.usecase.CreditAccountUseCase;
import com.enterprise.crud.application.usecase.DebitAccountService;
import com.enterprise.crud.application.usecase.DebitAccountUseCase;
import com.enterprise.crud.application.usecase.GetAccountService;
import com.enterprise.crud.application.usecase.GetAccountUseCase;
import com.enterprise.crud.application.usecase.ListAccountsService;
import com.enterprise.crud.application.usecase.ListAccountsUseCase;
import com.enterprise.crud.application.usecase.OpenAccountService;
import com.enterprise.crud.application.usecase.OpenAccountUseCase;
import com.enterprise.crud.domain.repository.AccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free application services. Each mutation issues a single {@code save}, whose transaction
 * lives in the persistence adapter, so the services need no transactional proxy.
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfiguration {

    @Bean
    OpenAccountUseCase openAccountUseCase(AccountRepository accountRepository) {
        return new OpenAccountService(accountRepository);
    }

    @Bean
    CreditAccountUseCase creditAccountUseCase(AccountRepository accountRepository) {
        return new CreditAccountService(accountRepository);
    }

    @Bean
    DebitAccountUseCase debitAccountUseCase(AccountRepository accountRepository) {
        return new DebitAccountService(accountRepository);
    }

    @Bean
    ChangeAccountStatusUseCase changeAccountStatusUseCase(AccountRepository accountRepository) {
        return new ChangeAccountStatusService(accountRepository);
    }

    @Bean
    GetAccountUseCase getAccountUseCase(AccountRepository accountRepository) {
        return new GetAccountService(accountRepository);
    }

    @Bean
    ListAccountsUseCase listAccountsUseCase(AccountRepository accountRepository) {
        return new ListAccountsService(accountRepository);
    }
}
