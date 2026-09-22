package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.OpenAccountCommand;
import com.enterprise.crud.application.dto.AccountResult;

public interface OpenAccountUseCase {

    AccountResult execute(OpenAccountCommand command);
}
