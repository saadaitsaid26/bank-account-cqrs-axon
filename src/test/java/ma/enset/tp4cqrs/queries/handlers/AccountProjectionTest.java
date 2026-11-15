package ma.enset.tp4cqrs.queries.handlers;

import ma.enset.tp4cqrs.common.enums.AccountStatus;
import ma.enset.tp4cqrs.common.enums.OperationType;
import ma.enset.tp4cqrs.events.AccountActivatedEvent;
import ma.enset.tp4cqrs.events.AccountBlockedEvent;
import ma.enset.tp4cqrs.events.AccountCreatedEvent;
import ma.enset.tp4cqrs.events.AccountCreditedEvent;
import ma.enset.tp4cqrs.events.AccountDebitedEvent;
import ma.enset.tp4cqrs.events.AccountSuspendedEvent;
import ma.enset.tp4cqrs.queries.entities.Account;
import ma.enset.tp4cqrs.queries.entities.AccountOperation;
import ma.enset.tp4cqrs.queries.queries.WatchAccountQuery;
import ma.enset.tp4cqrs.queries.repositories.AccountOperationRepository;
import ma.enset.tp4cqrs.queries.repositories.AccountRepository;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la projection (query side) : chaque @EventHandler, l'émission filtrée
 * des mises à jour temps réel et le @ResetHandler (replay).
 */
@ExtendWith(MockitoExtension.class)
class AccountProjectionTest {

    private static final String ACCOUNT_ID = "acc-1000";
    private static final String OTHER_ACCOUNT_ID = "acc-9999";
    private static final Instant EVENT_TIME = Instant.parse("2026-11-15T10:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountOperationRepository accountOperationRepository;

    @Mock
    private QueryUpdateEmitter queryUpdateEmitter;

    @InjectMocks
    private AccountEventHandler handler;

    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .accountId(ACCOUNT_ID)
                .balance(new BigDecimal("1000.00"))
                .currency("MAD")
                .status(AccountStatus.ACTIVATED)
                .createdAt(EVENT_TIME)
                .build();
    }

    @SuppressWarnings("unchecked")
    private EventMessage<?> eventMessageAt(Instant timestamp) {
        EventMessage<?> message = mock(EventMessage.class);
        when(message.getTimestamp()).thenReturn(timestamp);
        return message;
    }

    @Test
    @DisplayName("AccountCreatedEvent -> sauvegarde de la vue compte")
    void onCreatedEvent_shouldSaveAccountView() {
        handler.on(new AccountCreatedEvent(ACCOUNT_ID, new BigDecimal("1000.00"), "MAD"), eventMessageAt(EVENT_TIME));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account saved = captor.getValue();
        assertEquals(ACCOUNT_ID, saved.getAccountId());
        assertEquals(new BigDecimal("1000.00"), saved.getBalance());
        assertEquals("MAD", saved.getCurrency());
        assertEquals(AccountStatus.CREATED, saved.getStatus());
        assertEquals(EVENT_TIME, saved.getCreatedAt());
    }

    @Test
    @DisplayName("AccountCreditedEvent -> opération CREDIT, solde mis à jour, update émis filtré par accountId")
    void onCreditedEvent_shouldSaveOperationUpdateBalanceAndEmitFilteredUpdate() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountOperationRepository.save(any(AccountOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        handler.on(new AccountCreditedEvent(ACCOUNT_ID, new BigDecimal("250.00"), "MAD"), eventMessageAt(EVENT_TIME));

        // opération enregistrée
        ArgumentCaptor<AccountOperation> operationCaptor = ArgumentCaptor.forClass(AccountOperation.class);
        verify(accountOperationRepository).save(operationCaptor.capture());
        AccountOperation operation = operationCaptor.getValue();
        assertEquals(OperationType.CREDIT, operation.getType());
        assertEquals(new BigDecimal("250.00"), operation.getAmount());
        assertEquals(EVENT_TIME, operation.getTimestamp());
        assertEquals(account, operation.getAccount());

        // solde mis à jour
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertEquals(new BigDecimal("1250.00"), accountCaptor.getValue().getBalance());

        // émission temps réel filtrée par accountId
        ArgumentCaptor<Predicate<WatchAccountQuery>> predicateCaptor =
                ArgumentCaptor.forClass((Class) Predicate.class);
        verify(queryUpdateEmitter).emit(eq(WatchAccountQuery.class), predicateCaptor.capture(), same(operation));
        Predicate<WatchAccountQuery> predicate = predicateCaptor.getValue();
        assertTrue(predicate.test(new WatchAccountQuery(ACCOUNT_ID)));
        assertFalse(predicate.test(new WatchAccountQuery(OTHER_ACCOUNT_ID)));
    }

    @Test
    @DisplayName("AccountDebitedEvent -> opération DEBIT, solde décrémenté, update émis")
    void onDebitedEvent_shouldSaveOperationUpdateBalanceAndEmitFilteredUpdate() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountOperationRepository.save(any(AccountOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        handler.on(new AccountDebitedEvent(ACCOUNT_ID, new BigDecimal("400.00"), "MAD"), eventMessageAt(EVENT_TIME));

        ArgumentCaptor<AccountOperation> operationCaptor = ArgumentCaptor.forClass(AccountOperation.class);
        verify(accountOperationRepository).save(operationCaptor.capture());
        assertEquals(OperationType.DEBIT, operationCaptor.getValue().getType());

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertEquals(new BigDecimal("600.00"), accountCaptor.getValue().getBalance());

        verify(queryUpdateEmitter).emit(eq(WatchAccountQuery.class), any(Predicate.class), any(AccountOperation.class));
    }

    @Test
    @DisplayName("Événements de statut -> mise à jour du statut de la vue")
    void onStatusEvents_shouldUpdateStatus() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        handler.on(new AccountActivatedEvent(ACCOUNT_ID, AccountStatus.ACTIVATED));
        assertEquals(AccountStatus.ACTIVATED, account.getStatus());

        handler.on(new AccountSuspendedEvent(ACCOUNT_ID, AccountStatus.SUSPENDED));
        assertEquals(AccountStatus.SUSPENDED, account.getStatus());

        handler.on(new AccountBlockedEvent(ACCOUNT_ID, AccountStatus.BLOCKED));
        assertEquals(AccountStatus.BLOCKED, account.getStatus());

        verify(accountRepository, org.mockito.Mockito.times(3)).save(account);
    }

    @Test
    @DisplayName("@ResetHandler -> suppression des projections avant rejeu")
    void reset_shouldDeleteAllProjections() {
        handler.reset();
        verify(accountOperationRepository).deleteAllInBatch();
        verify(accountRepository).deleteAllInBatch();
    }
}
