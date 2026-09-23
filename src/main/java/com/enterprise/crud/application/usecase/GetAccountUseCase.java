package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.GetAccountQuery;
import com.enterprise.crud.application.dto.AccountResult;

public interface GetAccountUseCase {

    AccountResult execute(GetAccountQuery query);
}
