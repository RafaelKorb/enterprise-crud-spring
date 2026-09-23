package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.ChangeAccountStatusCommand;
import com.enterprise.crud.application.dto.AccountResult;

public interface ChangeAccountStatusUseCase {

    AccountResult execute(ChangeAccountStatusCommand command);
}
