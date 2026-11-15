package ma.enset.tp4cqrs.queries.controllers;

import lombok.extern.slf4j.Slf4j;
import ma.enset.tp4cqrs.queries.dto.AccountStatementDTO;
import ma.enset.tp4cqrs.queries.entities.Account;
import ma.enset.tp4cqrs.queries.entities.AccountOperation;
import ma.enset.tp4cqrs.queries.queries.GetAllAccountsQuery;
import ma.enset.tp4cqrs.queries.queries.GetAccountByIdQuery;
import ma.enset.tp4cqrs.queries.queries.GetAccountStatementQuery;
import ma.enset.tp4cqrs.queries.queries.WatchAccountQuery;
import ma.enset.tp4cqrs.queries.services.ReplayEventsService;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Query side : lectures via le QueryGateway, suivi temps réel SSE (subscription query)
 * et déclenchement du replay des projections.
 */
@RestController
@RequestMapping("/query")
@Slf4j
public class AccountQueryController {

    private final QueryGateway queryGateway;
    private final ReplayEventsService replayEventsService;

    public AccountQueryController(QueryGateway queryGateway, ReplayEventsService replayEventsService) {
        this.queryGateway = queryGateway;
        this.replayEventsService = replayEventsService;
    }

    @GetMapping("/accounts")
    public CompletableFuture<List<Account>> accountList() {
        return queryGateway.query(new GetAllAccountsQuery(), ResponseTypes.multipleInstancesOf(Account.class));
    }

    @GetMapping("/accounts/{accountId}")
    public CompletableFuture<Account> accountById(@PathVariable String accountId) {
        return queryGateway.query(new GetAccountByIdQuery(accountId), ResponseTypes.instanceOf(Account.class));
    }

    @GetMapping("/accounts/{accountId}/statement")
    public CompletableFuture<AccountStatementDTO> accountStatement(@PathVariable String accountId) {
        return queryGateway.query(new GetAccountStatementQuery(accountId), ResponseTypes.instanceOf(AccountStatementDTO.class));
    }

    /**
     * Suivi temps réel des opérations d'un compte (Server-Sent Events).
     * Réponse initiale : dernière opération connue (ou vue vide), puis chaque nouvelle opération est poussée.
     */
    @GetMapping(value = "/accounts/{accountId}/watch", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AccountOperation> watchAccountOperations(@PathVariable String accountId) {
        SubscriptionQueryResult<AccountOperation, AccountOperation> result = queryGateway.subscriptionQuery(
                new WatchAccountQuery(accountId),
                ResponseTypes.instanceOf(AccountOperation.class),
                ResponseTypes.instanceOf(AccountOperation.class));
        return result.initialResult().concatWith(result.updates());
    }

    @GetMapping("/replayEvents")
    public String replayEvents() {
        replayEventsService.replay();
        return String.format("Replaying events for projection '%s'", ReplayEventsService.PROCESSOR_NAME);
    }

    /**
     * Remède de changement de backend d'event store (postgres ↔ Axon Server) :
     * purge les tokens de suivi persistés (type incompatible selon le backend) et les vues,
     * puis reconstruit toutes les projections depuis le début de l'event store courant.
     */
    @GetMapping("/resetTokens")
    public String resetTokens() {
        replayEventsService.resetTokens();
        return String.format("Tracking tokens of projection '%s' reset (projections rebuilt)", ReplayEventsService.PROCESSOR_NAME);
    }
}
