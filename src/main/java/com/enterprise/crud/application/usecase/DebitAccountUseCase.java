package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.DebitAccountCommand;
import com.enterprise.crud.application.dto.AccountResult;

public interface DebitAccountUseCase {

    AccountResult execute(DebitAccountCommand command);
}
