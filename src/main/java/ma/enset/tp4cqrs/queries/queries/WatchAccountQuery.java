package ma.enset.tp4cqrs.queries.queries;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query d'abonnement (subscription query) pour le suivi temps réel d'un compte via SSE :
 * la réponse initiale est la dernière opération connue (ou une vue vide),
 * les mises à jour sont émises par les event handlers via QueryUpdateEmitter.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchAccountQuery {
    private String accountId;
}
