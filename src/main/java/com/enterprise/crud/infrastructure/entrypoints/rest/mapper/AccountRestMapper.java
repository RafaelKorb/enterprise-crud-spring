package com.enterprise.crud.infrastructure.entrypoints.rest.mapper;

import com.enterprise.crud.application.dto.AccountPageResult;
import com.enterprise.crud.application.dto.AccountResult;
import com.enterprise.crud.application.dto.ChangeAccountStatusCommand;
import com.enterprise.crud.application.dto.CreditAccountCommand;
import com.enterprise.crud.application.dto.DebitAccountCommand;
import com.enterprise.crud.application.dto.OpenAccountCommand;
import com.enterprise.crud.infrastructure.entrypoints.rest.dto.AccountPageResponse;
import com.enterprise.crud.infrastructure.entrypoints.rest.dto.AccountResponse;
import com.enterprise.crud.infrastructure.entrypoints.rest.dto.AmountRequest;
import com.enterprise.crud.infrastructure.entrypoints.rest.dto.ChangeAccountStatusRequest;
import com.enterprise.crud.infrastructure.entrypoints.rest.dto.OpenAccountRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AccountRestMapper {

    OpenAccountCommand toCommand(OpenAccountRequest request);

    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "amount", source = "request.amount")
    CreditAccountCommand toCreditCommand(UUID accountId, AmountRequest request);

    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "amount", source = "request.amount")
    DebitAccountCommand toDebitCommand(UUID accountId, AmountRequest request);

    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "targetStatus", source = "request.status")
    ChangeAccountStatusCommand toCommand(UUID accountId, ChangeAccountStatusRequest request);

    AccountResponse toResponse(AccountResult result);

    /** MapStruct does not unwrap {@code Optional}, so the page envelope is mapped by hand. */
    default AccountPageResponse toResponse(AccountPageResult page) {
        return new AccountPageResponse(page.items().stream().map(this::toResponse).toList(),
                page.nextCursor().orElse(null));
    }
}
