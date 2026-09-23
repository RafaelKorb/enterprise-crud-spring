package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.CreditAccountCommand;
import com.enterprise.crud.application.dto.AccountResult;

public interface CreditAccountUseCase {

    AccountResult execute(CreditAccountCommand command);
}
