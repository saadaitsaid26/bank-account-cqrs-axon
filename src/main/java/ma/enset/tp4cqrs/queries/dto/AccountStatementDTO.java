package ma.enset.tp4cqrs.queries.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.enset.tp4cqrs.queries.entities.Account;
import ma.enset.tp4cqrs.queries.entities.AccountOperation;

import java.util.List;

/** Relevé de compte : vue du compte + ses opérations triées chronologiquement. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountStatementDTO {
    private Account account;
    private List<AccountOperation> operations;
}
