package ma.enset.tp4cqrs.commands.controllers;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import ma.enset.tp4cqrs.commands.commands.ActivateAccountCommand;
import ma.enset.tp4cqrs.commands.commands.BlockAccountCommand;
import ma.enset.tp4cqrs.commands.commands.CreateAccountCommand;
import ma.enset.tp4cqrs.commands.commands.CreditAccountCommand;
import ma.enset.tp4cqrs.commands.commands.DebitAccountCommand;
import ma.enset.tp4cqrs.commands.commands.SuspendAccountCommand;
import ma.enset.tp4cqrs.commands.dto.AccountOperationRequestDTO;
import ma.enset.tp4cqrs.commands.dto.CreateAccountRequestDTO;
import ma.enset.tp4cqrs.common.exceptions.AccountNotFoundException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command side : reçoit les commandes HTTP et les dispatche via le CommandGateway.
 * Expose aussi la lecture des événements de l'event store pour un agrégat donné.
 */
@RestController
@RequestMapping("/commands")
@Slf4j
public class AccountCommandController {

    private final CommandGateway commandGateway;
    private final EventStore eventStore;

    public AccountCommandController(CommandGateway commandGateway, EventStore eventStore) {
        this.commandGateway = commandGateway;
        this.eventStore = eventStore;
    }

    @PostMapping("/accounts")
    public CompletableFuture<ResponseEntity<String>> createAccount(@Valid @RequestBody CreateAccountRequestDTO request) {
        String accountId = UUID.randomUUID().toString();
        return commandGateway.send(new CreateAccountCommand(accountId, request.getInitialBalance(), request.getCurrency()))
                .thenApply(result -> ResponseEntity
                        .created(URI.create("/query/accounts/" + accountId))
                        .body(accountId));
    }

    @PostMapping("/accounts/{accountId}/credit")
    public CompletableFuture<String> creditAccount(@PathVariable String accountId,
                                                   @Valid @RequestBody AccountOperationRequestDTO request) {
        return commandGateway.send(new CreditAccountCommand(accountId, request.getAmount(), request.getCurrency()))
                .thenApply(result -> accountId);
    }

    @PostMapping("/accounts/{accountId}/debit")
    public CompletableFuture<String> debitAccount(@PathVariable String accountId,
                                                  @Valid @RequestBody AccountOperationRequestDTO request) {
        return commandGateway.send(new DebitAccountCommand(accountId, request.getAmount(), request.getCurrency()))
                .thenApply(result -> accountId);
    }

    @PostMapping("/accounts/{accountId}/activate")
    public CompletableFuture<String> activateAccount(@PathVariable String accountId) {
        return commandGateway.send(new ActivateAccountCommand(accountId)).thenApply(result -> accountId);
    }

    @PostMapping("/accounts/{accountId}/suspend")
    public CompletableFuture<String> suspendAccount(@PathVariable String accountId) {
        return commandGateway.send(new SuspendAccountCommand(accountId)).thenApply(result -> accountId);
    }

    @PostMapping("/accounts/{accountId}/block")
    public CompletableFuture<String> blockAccount(@PathVariable String accountId) {
        return commandGateway.send(new BlockAccountCommand(accountId)).thenApply(result -> accountId);
    }

    /**
     * Lecture des événements d'un agrégat depuis l'event store
     * (reproduit l'endpoint du cours : eventStore.readEvents(id).asStream()).
     */
    @GetMapping("/events/{accountId}")
    public List<? extends DomainEventMessage<?>> accountEvents(@PathVariable String accountId) {
        List<? extends DomainEventMessage<?>> events = eventStore.readEvents(accountId).asStream().toList();
        if (events.isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }
        return events;
    }

    @GetMapping("/health-check")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.status(HttpStatus.OK).body("Command side is running");
    }
}
