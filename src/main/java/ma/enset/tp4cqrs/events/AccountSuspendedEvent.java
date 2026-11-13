package ma.enset.tp4cqrs.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.enset.tp4cqrs.common.enums.AccountStatus;

/** Fait : le statut du compte est passé à SUSPENDED. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountSuspendedEvent {
    private String accountId;
    private AccountStatus status;
}
