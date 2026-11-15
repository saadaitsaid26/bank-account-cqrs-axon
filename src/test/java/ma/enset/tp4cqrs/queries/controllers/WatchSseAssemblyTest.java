package ma.enset.tp4cqrs.queries.controllers;

import ma.enset.tp4cqrs.common.enums.OperationType;
import ma.enset.tp4cqrs.queries.entities.AccountOperation;
import ma.enset.tp4cqrs.queries.queries.WatchAccountQuery;
import ma.enset.tp4cqrs.queries.services.ReplayEventsService;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test de l'assemblage du flux SSE du watch : réponse initiale concaténée avec les mises à jour,
 * vérifié avec StepVerifier (Reactor).
 */
class WatchSseAssemblyTest {

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("watch : initialResult concaténé avec updates, dans l'ordre (SSE)")
    void watch_shouldConcatenateInitialResultWithUpdates() {
        QueryGateway queryGateway = mock(QueryGateway.class);
        ReplayEventsService replayEventsService = mock(ReplayEventsService.class);
        SubscriptionQueryResult<AccountOperation, AccountOperation> subscription =
                (SubscriptionQueryResult<AccountOperation, AccountOperation>) mock(SubscriptionQueryResult.class);

        AccountOperation initial = new AccountOperation();
        AccountOperation credit = AccountOperation.builder()
                .id(1L).amount(new BigDecimal("120.50")).type(OperationType.CREDIT).currency("MAD").build();
        AccountOperation debit = AccountOperation.builder()
                .id(2L).amount(new BigDecimal("30.00")).type(OperationType.DEBIT).currency("MAD").build();

        when(subscription.initialResult()).thenReturn(Mono.just(initial));
        when(subscription.updates()).thenReturn(Flux.just(credit, debit));
        when(queryGateway.subscriptionQuery(any(WatchAccountQuery.class), any(ResponseType.class), any(ResponseType.class)))
                .thenReturn(subscription);

        AccountQueryController controller = new AccountQueryController(queryGateway, replayEventsService);

        StepVerifier.create(controller.watchAccountOperations("acc-1"))
                .expectNext(initial)
                .expectNext(credit)
                .expectNext(debit)
                .verifyComplete();

        verify(queryGateway).subscriptionQuery(any(WatchAccountQuery.class), any(ResponseType.class), any(ResponseType.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("watch : si aucune opération n'existe, la réponse initiale est une vue vide")
    void watch_emptyInitialResult_isEmitted() {
        SubscriptionQueryResult<AccountOperation, AccountOperation> subscription =
                (SubscriptionQueryResult<AccountOperation, AccountOperation>) mock(SubscriptionQueryResult.class);
        QueryGateway queryGateway = mock(QueryGateway.class);
        when(subscription.initialResult()).thenReturn(Mono.just(new AccountOperation()));
        when(subscription.updates()).thenReturn(Flux.never());
        when(queryGateway.subscriptionQuery(any(WatchAccountQuery.class), any(ResponseType.class), any(ResponseType.class)))
                .thenReturn(subscription);

        AccountQueryController controller = new AccountQueryController(queryGateway, mock(ReplayEventsService.class));

        StepVerifier.create(controller.watchAccountOperations("acc-unknown").take(1))
                .expectNextMatches(op -> op.getId() == null && op.getType() == null)
                .thenCancel()
                .verify();
    }
}
