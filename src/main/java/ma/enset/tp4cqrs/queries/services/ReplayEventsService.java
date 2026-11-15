package ma.enset.tp4cqrs.queries.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.tp4cqrs.queries.handlers.AccountEventHandler;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.springframework.stereotype.Service;

/**
 * Deux opérations d'exploitation sur la projection « account-projection » :
 *
 * <ul>
 *   <li>{@link #replay()} : rejeu Axon natif (shutDown → resetTokens → start). Le {@code @ResetHandler}
 *       supprime les vues, puis tout l'event store <em>courant</em> est rejoué. À utiliser quand le
 *       backend d'event store n'a pas changé (token compatible).</li>
 *   <li>{@link #resetTokens()} : remède de changement de backend d'event store (postgres ↔ Axon Server).
 *       Les tokens de suivi persistés (table {@code token_entry}) portent un type dépendant du backend
 *       (GapAwareTrackingToken pour un event store JPA, GlobalSequence/Index pour Axon Server) : les
 *       réutiliser après un changement provoque « Token ... is of the wrong type » et bloque la
 *       projection. Cette opération supprime les tokens via l'API publique {@link TokenStore}
 *       (aucun SQL direct sur les tables internes Axon), purge les vues, puis redémarre le processor,
 *       qui repart du début (tail) de l'event store courant et reconstruit toutes les projections.</li>
 * </ul>
 */
@Service
@Slf4j
@AllArgsConstructor
public class ReplayEventsService {

    public static final String PROCESSOR_NAME = "account-projection";

    private final EventProcessingConfiguration eventProcessingConfiguration;
    private final TokenStoreCleaner tokenStoreCleaner;
    private final AccountEventHandler accountEventHandler;

    /** Rejeu natif Axon (même backend d'event store). */
    public void replay() {
        eventProcessingConfiguration.eventProcessor(PROCESSOR_NAME, TrackingEventProcessor.class)
                .ifPresentOrElse(processor -> {
                    log.info("######## Replaying events for processor '{}'", PROCESSOR_NAME);
                    awaitShutdown(processor);
                    processor.resetTokens();
                    processor.start();
                    log.info("######## Replay triggered for processor '{}'", PROCESSOR_NAME);
                }, () -> {
                    throw new IllegalStateException(
                            String.format("Event processor '%s' not found", PROCESSOR_NAME));
                });
    }

    /**
     * Changement de backend d'event store : purge des tokens persistés + des vues, puis reprise
     * depuis le début de l'event store courant (reconstruction complète des projections).
     */
    public void resetTokens() {
        eventProcessingConfiguration.eventProcessor(PROCESSOR_NAME, TrackingEventProcessor.class)
                .ifPresentOrElse(processor -> {
                    log.info("######## Resetting tracking tokens of processor '{}' (backend switch) ########", PROCESSOR_NAME);
                    awaitShutdown(processor);
                    // Purge des vues avant reconstruction (même logique que le @ResetHandler)
                    accountEventHandler.reset();
                    // Suppression transactionnelle des tokens persistés via l'API publique TokenStore
                    tokenStoreCleaner.deleteTokens(PROCESSOR_NAME);
                    // Sans token, le TrackingEventProcessor repart du tail de l'event store courant
                    processor.start();
                    log.info("######## Tracking tokens reset for processor '{}' ########", PROCESSOR_NAME);
                }, () -> {
                    throw new IllegalStateException(
                            String.format("Event processor '%s' not found", PROCESSOR_NAME));
                });
    }

    /** Attend l'arrêt effectif du processor (claims libérés) avant toute manipulation des tokens. */
    private void awaitShutdown(TrackingEventProcessor processor) {
        processor.shutdownAsync().join();
    }
}
