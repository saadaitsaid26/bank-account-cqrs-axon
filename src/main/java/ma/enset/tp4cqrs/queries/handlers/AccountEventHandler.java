package ma.enset.tp4cqrs.queries.handlers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.tp4cqrs.common.enums.AccountStatus;
import ma.enset.tp4cqrs.common.enums.OperationType;
import ma.enset.tp4cqrs.common.exceptions.AccountNotFoundException;
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
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query side : alimente les projections JPA à partir des événements
 * et pousse les mises à jour temps réel aux abonnements (watch SSE).
 */
@Service
@ProcessingGroup("account-projection")
@Transactional
@Slf4j
@AllArgsConstructor
public class AccountEventHandler {

    private final AccountRepository accountRepository;
    private final AccountOperationRepository accountOperationRepository;
    private final QueryUpdateEmitter queryUpdateEmitter;

    /** Rejouabilité : les vues sont supprimées avant le rejeu complet des événements. */
    @ResetHandler
    public void reset() {
        log.info("######## Resetting projections (delete all accounts and operations) ########");
        accountOperationRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
    }

    @EventHandler
    public void on(AccountCreatedEvent event, EventMessage<?> eventMessage) {
        log.info("######## Query side - AccountCreatedEvent received: {}", event);
        Account account = Account.builder()
                .accountId(event.getAccountId())
                .balance(event.getInitialBalance())
                .currency(event.getCurrency())
                .status(AccountStatus.CREATED)
                .createdAt(eventMessage.getTimestamp())
                .build();
        accountRepository.save(account);
    }

    @EventHandler
    public void on(AccountActivatedEvent event) {
        log.info("######## Query side - AccountActivatedEvent received: {}", event);
        updateStatus(event.getAccountId(), event.getStatus());
    }

    @EventHandler
    public void on(AccountSuspendedEvent event) {
        log.info("######## Query side - AccountSuspendedEvent received: {}", event);
        updateStatus(event.getAccountId(), event.getStatus());
    }

    @EventHandler
    public void on(AccountBlockedEvent event) {
        log.info("######## Query side - AccountBlockedEvent received: {}", event);
        updateStatus(event.getAccountId(), event.getStatus());
    }

    @EventHandler
    public void on(AccountCreditedEvent event, EventMessage<?> eventMessage) {
        log.info("######## Query side - AccountCreditedEvent received: {}", event);
        Account account = loadAccount(event.getAccountId());
        AccountOperation operation = accountOperationRepository.save(AccountOperation.builder()
                .account(account)
                .timestamp(eventMessage.getTimestamp())
                .amount(event.getAmount())
                .type(OperationType.CREDIT)
                .currency(event.getCurrency())
                .build());
        account.setBalance(account.getBalance().add(event.getAmount()));
        accountRepository.save(account);
        emitOperationUpdate(operation);
    }

    @EventHandler
    public void on(AccountDebitedEvent event, EventMessage<?> eventMessage) {
        log.info("######## Query side - AccountDebitedEvent received: {}", event);
        Account account = loadAccount(event.getAccountId());
        AccountOperation operation = accountOperationRepository.save(AccountOperation.builder()
                .account(account)
                .timestamp(eventMessage.getTimestamp())
                .amount(event.getAmount())
                .type(OperationType.DEBIT)
                .currency(event.getCurrency())
                .build());
        account.setBalance(account.getBalance().subtract(event.getAmount()));
        accountRepository.save(account);
        emitOperationUpdate(operation);
    }

    private Account loadAccount(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private void updateStatus(String accountId, AccountStatus status) {
        Account account = loadAccount(accountId);
        account.setStatus(status);
        accountRepository.save(account);
    }

    /** Pousse l'opération aux abonnements actifs du watch temps réel (filtrés par compte). */
    private void emitOperationUpdate(AccountOperation operation) {
        String accountId = operation.getAccount().getAccountId();
        queryUpdateEmitter.emit(WatchAccountQuery.class,
                query -> query.getAccountId().equals(accountId),
                operation);
    }
}
