package ma.enset.tp4cqrs.queries.queries;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Query : relevé d'un compte (compte + liste de ses opérations). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetAccountStatementQuery {
    private String accountId;
}
