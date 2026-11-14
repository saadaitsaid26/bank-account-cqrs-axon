package ma.enset.tp4cqrs.queries.queries;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Query : un compte par identifiant. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetAccountByIdQuery {
    private String accountId;
}
