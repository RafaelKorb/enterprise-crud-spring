package com.enterprise.crud.application.usecase;

import com.enterprise.crud.application.dto.ListAccountsQuery;
import com.enterprise.crud.application.dto.AccountPageResult;

public interface ListAccountsUseCase {

    AccountPageResult execute(ListAccountsQuery query);
}
