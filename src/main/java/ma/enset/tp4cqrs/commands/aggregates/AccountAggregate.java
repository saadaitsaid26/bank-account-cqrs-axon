package ma.enset.tp4cqrs.commands.aggregates;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.tp4cqrs.commands.commands.ActivateAccountCommand;
import ma.enset.tp4cqrs.commands.commands.BlockAccountCommand;
import ma.enset.tp4cqrs.commands.commands.CreateAccountCommand;
import ma.enset.tp4cqrs.commands.commands.CreditAccountCommand;
import ma.enset.tp4cqrs.commands.commands.DebitAccountCommand;
import ma.enset.tp4cqrs.commands.commands.SuspendAccountCommand;
import ma.enset.tp4cqrs.common.enums.AccountStatus;
import ma.enset.tp4cqrs.common.exceptions.BalanceInsufficientException;
import ma.enset.tp4cqrs.common.exceptions.CurrencyMismatchException;
import ma.enset.tp4cqrs.common.exceptions.InvalidAccountStatusException;
import ma.enset.tp4cqrs.common.exceptions.NegativeAmountException;
import ma.enset.tp4cqrs.common.exceptions.NegativeInitialBalanceException;
import ma.enset.tp4cqrs.events.AccountActivatedEvent;
import ma.enset.tp4cqrs.events.AccountBlockedEvent;
import ma.enset.tp4cqrs.events.AccountCreatedEvent;
import ma.enset.tp4cqrs.events.AccountCreditedEvent;
import ma.enset.tp4cqrs.events.AccountDebitedEvent;
import ma.enset.tp4cqrs.events.AccountSuspendedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.math.BigDecimal;

/**
 * Agrégat event-sourced du compte bancaire.
 * <p>
 * Fonction de décision = {@code @CommandHandler} (valide les règles métier puis émet des événements),
 * fonction d'évolution = {@code @EventSourcingHandler} (applique les événements sans décision).
 */
@Aggregate
@Slf4j
@Data
@NoArgsConstructor
public class AccountAggregate {

    @AggregateIdentifier
    private String accountId;
    private BigDecimal balance;
    private String currency;
    private AccountStatus status;

    // ---------------------------------------------------------------------
    // Création (double événement démontré dans le cours : Created + Activated)
    // ---------------------------------------------------------------------

    @CommandHandler
    public AccountAggregate(CreateAccountCommand command) {
        log.info("###### CreateAccountCommand received: {}", command);
        if (command.getInitialBalance() == null || command.getInitialBalance().signum() < 0) {
            throw new NegativeInitialBalanceException(
                    String.format("Initial balance cannot be negative (value: %s)", command.getInitialBalance()));
        }
        AggregateLifecycle.apply(new AccountCreatedEvent(
                command.getAccountId(),
                command.getInitialBalance(),
                command.getCurrency()));
        // Le compte est automatiquement activé à la création (comportement démontré dans la démo du cours).
        AggregateLifecycle.apply(new AccountActivatedEvent(command.getAccountId(), AccountStatus.ACTIVATED));
    }

    @EventSourcingHandler
    public void on(AccountCreatedEvent event) {
        log.info("###### AccountCreatedEvent occurred: {}", event);
        this.accountId = event.getAccountId();
        this.balance = event.getInitialBalance();
        this.currency = event.getCurrency();
        this.status = AccountStatus.CREATED;
    }

    @EventSourcingHandler
    public void on(AccountActivatedEvent event) {
        log.info("###### AccountActivatedEvent occurred: {}", event);
        this.status = event.getStatus();
    }

    // ---------------------------------------------------------------------
    // Crédit
    // ---------------------------------------------------------------------

    @CommandHandler
    public void handle(CreditAccountCommand command) {
        log.info("###### CreditAccountCommand received: {}", command);
        assertAmountIsPositive(command.getAmount());
        assertAccountIsActive("credit");
        assertCurrencyMatches(command.getCurrency());
        AggregateLifecycle.apply(new AccountCreditedEvent(command.getAccountId(), command.getAmount(), command.getCurrency()));
    }

    @EventSourcingHandler
    public void on(AccountCreditedEvent event) {
        log.info("###### AccountCreditedEvent occurred: {}", event);
        this.balance = this.balance.add(event.getAmount());
    }

    // ---------------------------------------------------------------------
    // Débit
    // ---------------------------------------------------------------------

    @CommandHandler
    public void handle(DebitAccountCommand command) {
        log.info("###### DebitAccountCommand received: {}", command);
        assertAmountIsPositive(command.getAmount());
        assertAccountIsActive("debit");
        assertCurrencyMatches(command.getCurrency());
        if (this.balance.compareTo(command.getAmount()) < 0) {
            throw new BalanceInsufficientException(String.format(
                    "Insufficient balance: %s %s available, %s %s requested for debit of account %s",
                    this.balance, this.currency, command.getAmount(), command.getCurrency(), command.getAccountId()));
        }
        AggregateLifecycle.apply(new AccountDebitedEvent(command.getAccountId(), command.getAmount(), command.getCurrency()));
    }

    @EventSourcingHandler
    public void on(AccountDebitedEvent event) {
        log.info("###### AccountDebitedEvent occurred: {}", event);
        this.balance = this.balance.subtract(event.getAmount());
    }

    // ---------------------------------------------------------------------
    // Cycle de vie du statut
    // ---------------------------------------------------------------------

    @CommandHandler
    public String handle(ActivateAccountCommand command) {
        log.info("###### ActivateAccountCommand received: {}", command);
        if (this.status == AccountStatus.ACTIVATED) {
            throw new InvalidAccountStatusException(
                    String.format("Account %s is already ACTIVATED", this.accountId));
        }
        if (this.status == AccountStatus.BLOCKED) {
            throw new InvalidAccountStatusException(
                    String.format("Account %s is BLOCKED: a blocked account cannot be activated", this.accountId));
        }
        AggregateLifecycle.apply(new AccountActivatedEvent(command.getAccountId(), AccountStatus.ACTIVATED));
        return this.accountId;
    }

    @CommandHandler
    public String handle(SuspendAccountCommand command) {
        log.info("###### SuspendAccountCommand received: {}", command);
        if (this.status == AccountStatus.SUSPENDED) {
            throw new InvalidAccountStatusException(
                    String.format("Account %s is already SUSPENDED", this.accountId));
        }
        if (this.status == AccountStatus.BLOCKED) {
            throw new InvalidAccountStatusException(
                    String.format("Account %s is BLOCKED: a blocked account cannot be suspended", this.accountId));
        }
        if (this.status == AccountStatus.CREATED) {
            throw new InvalidAccountStatusException(
                    String.format("Account %s is not activated yet: only an ACTIVATED account can be suspended", this.accountId));
        }
        AggregateLifecycle.apply(new AccountSuspendedEvent(command.getAccountId(), AccountStatus.SUSPENDED));
        return this.accountId;
    }

    @CommandHandler
    public String handle(BlockAccountCommand command) {
        log.info("###### BlockAccountCommand received: {}", command);
        if (this.status == AccountStatus.BLOCKED) {
            throw new InvalidAccountStatusException(
                    String.format("Account %s is already BLOCKED", this.accountId));
        }
        AggregateLifecycle.apply(new AccountBlockedEvent(command.getAccountId(), AccountStatus.BLOCKED));
        return this.accountId;
    }

    @EventSourcingHandler
    public void on(AccountSuspendedEvent event) {
        log.info("###### AccountSuspendedEvent occurred: {}", event);
        this.status = event.getStatus();
    }

    @EventSourcingHandler
    public void on(AccountBlockedEvent event) {
        log.info("###### AccountBlockedEvent occurred: {}", event);
        this.status = event.getStatus();
    }

    // ---------------------------------------------------------------------
    // Règles métier (fonction de décision)
    // ---------------------------------------------------------------------

    private void assertAmountIsPositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new NegativeAmountException(String.format("Amount must be strictly positive (value: %s)", amount));
        }
    }

    private void assertAccountIsActive(String operation) {
        if (this.status != AccountStatus.ACTIVATED) {
            String extra = this.status == AccountStatus.BLOCKED
                    ? " (a BLOCKED account is terminal and cannot receive any operation)"
                    : "";
            throw new InvalidAccountStatusException(String.format(
                    "Account %s must be ACTIVATED to be %sed (current status: %s)%s",
                    this.accountId, operation, this.status, extra));
        }
    }

    private void assertCurrencyMatches(String currency) {
        if (currency == null || !currency.equalsIgnoreCase(this.currency)) {
            throw new CurrencyMismatchException(String.format(
                    "Currency mismatch: account %s operates in %s, received %s",
                    this.accountId, this.currency, currency));
        }
    }
}
