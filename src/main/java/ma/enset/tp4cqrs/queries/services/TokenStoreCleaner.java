package ma.enset.tp4cqrs.queries.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suppression transactionnelle des tokens de suivi persistés d'un event processor,
 * via l'API publique {@link TokenStore} (aucun SQL direct sur la table interne token_entry).
 *
 * Utilisée comme remède au changement de backend d'event store (postgres ↔ Axon Server) :
 * les tokens y sont de types incompatibles (GapAwareTrackingToken vs GlobalSequence/Index).
 * L'appel doit être transactionnel : {@code JpaTokenStore#deleteToken} exécute un
 * UPDATE/DELETE JPA qui échoue hors transaction (TransactionRequiredException).
 */
@Service
@Slf4j
@AllArgsConstructor
public class TokenStoreCleaner {

    private final TokenStore tokenStore;

    @Transactional
    public void deleteTokens(String processorName) {
        for (int segment : tokenStore.fetchSegments(processorName)) {
            // deleteToken exige que le token soit claimé par ce nœud : après l'arrêt du processor
            // le claim est libéré (owner null) — on le (re)claim d'abord via fetchToken.
            tokenStore.fetchToken(processorName, segment);
            tokenStore.deleteToken(processorName, segment);
            log.info("######## Deleted tracking token of processor '{}' segment {} ########", processorName, segment);
        }
    }
}
