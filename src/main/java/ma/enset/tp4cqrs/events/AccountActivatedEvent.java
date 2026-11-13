package ma.enset.tp4cqrs.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.enset.tp4cqrs.common.enums.AccountStatus;

/** Fait : le statut du compte est passé à ACTIVATED. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountActivatedEvent {
    private String accountId;
    private AccountStatus status;
}
