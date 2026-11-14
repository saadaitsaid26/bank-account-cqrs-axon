package ma.enset.tp4cqrs.commands.aggregates;

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
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

/**
 * Tests de la fonction de décision (règles métier dans l'agrégat) via AggregateTestFixture.
 */
class AccountAggregateTest {

    private static final String ACCOUNT_ID = "acc-0001";
    private static final String CURRENCY = "MAD";

    private FixtureConfiguration<AccountAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(AccountAggregate.class);
    }

    private AccountCreatedEvent createdEvent(BigDecimal initialBalance) {
        return new AccountCreatedEvent(ACCOUNT_ID, initialBalance, CURRENCY);
    }

    private AccountActivatedEvent activatedEvent() {
        return new AccountActivatedEvent(ACCOUNT_ID, AccountStatus.ACTIVATED);
    }

    // ------------------------------------------------------------------
    // Création
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Créer un compte émet Created + Activated (auto-activation) et initialise l'état")
    void createAccount_shouldEmitCreatedAndActivatedEvents() {
        fixture.givenNoPriorActivity()
                .when(new CreateAccountCommand(ACCOUNT_ID, new BigDecimal("1000.00"), CURRENCY))
                .expectSuccessfulHandlerExecution()
                .expectEvents(createdEvent(new BigDecimal("1000.00")), activatedEvent())
                .expectState(state -> {
                    org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("1000.00"), state.getBalance());
                    org.junit.jupiter.api.Assertions.assertEquals(AccountStatus.ACTIVATED, state.getStatus());
                    org.junit.jupiter.api.Assertions.assertEquals(CURRENCY, state.getCurrency());
                    org.junit.jupiter.api.Assertions.assertEquals(ACCOUNT_ID, state.getAccountId());
                });
    }

    @Test
    @DisplayName("Solde initial négatif refusé (NegativeInitialBalanceException)")
    void createAccount_negativeInitialBalance_shouldThrow() {
        fixture.givenNoPriorActivity()
                .when(new CreateAccountCommand(ACCOUNT_ID, new BigDecimal("-50.00"), CURRENCY))
                .expectException(NegativeInitialBalanceException.class)
                .expectExceptionMessage("Initial balance cannot be negative (value: -50.00)");
    }

    @Test
    @DisplayName("Solde initial nul accepté")
    void createAccount_zeroInitialBalance_shouldBeAccepted() {
        fixture.givenNoPriorActivity()
                .when(new CreateAccountCommand(ACCOUNT_ID, BigDecimal.ZERO, CURRENCY))
                .expectEvents(createdEvent(BigDecimal.ZERO), activatedEvent());
    }

    // ------------------------------------------------------------------
    // Crédit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Créditer un compte ACTIVATED émet AccountCreditedEvent et met à jour le solde")
    void creditAccount_onActivatedAccount_shouldEmitCreditedEvent() {
        fixture.given(createdEvent(new BigDecimal("1000.00")), activatedEvent())
                .when(new CreditAccountCommand(ACCOUNT_ID, new BigDecimal("250.50"), CURRENCY))
                .expectEvents(new AccountCreditedEvent(ACCOUNT_ID, new BigDecimal("250.50"), CURRENCY))
                .expectState(state -> org.junit.jupiter.api.Assertions.assertEquals(
                        new BigDecimal("1250.50"), state.getBalance()));
    }

    @Test
    @DisplayName("Crédit d'un montant négatif ou nul refusé")
    void creditAccount_nonPositiveAmount_shouldThrow() {
        fixture.given(createdEvent(new BigDecimal("1000.00")), activatedEvent())
                .when(new CreditAccountCommand(ACCOUNT_ID, BigDecimal.ZERO, CURRENCY))
                .expectException(NegativeAmountException.class);
    }

    @Test
    @DisplayName("Crédit dans une devise différente refusé")
    void creditAccount_currencyMismatch_shouldThrow() {
        fixture.given(createdEvent(new BigDecimal("1000.00")), activatedEvent())
                .when(new CreditAccountCommand(ACCOUNT_ID, new BigDecimal("100.00"), "EUR"))
                .expectException(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("Créditer un compte SUSPENDED refusé")
    void creditAccount_suspendedAccount_shouldThrow() {
        fixture.given(
                        createdEvent(new BigDecimal("1000.00")),
                        activatedEvent(),
                        new AccountSuspendedEvent(ACCOUNT_ID, AccountStatus.SUSPENDED))
                .when(new CreditAccountCommand(ACCOUNT_ID, new BigDecimal("100.00"), CURRENCY))
                .expectException(InvalidAccountStatusException.class)
                .expectExceptionMessage("Account acc-0001 must be ACTIVATED to be credited (current status: SUSPENDED)");
    }

    // ------------------------------------------------------------------
    // Débit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Débiter un compte ACTIVATED avec solde suffisant émet AccountDebitedEvent")
    void debitAccount_withSufficientBalance_shouldEmitDebitedEvent() {
        fixture.given(createdEvent(new BigDecimal("1000.00")), activatedEvent())
                .when(new DebitAccountCommand(ACCOUNT_ID, new BigDecimal("300.00"), CURRENCY))
                .expectEvents(new AccountDebitedEvent(ACCOUNT_ID, new BigDecimal("300.00"), CURRENCY))
                .expectState(state -> org.junit.jupiter.api.Assertions.assertEquals(
                        new BigDecimal("700.00"), state.getBalance()));
    }

    @Test
    @DisplayName("Débit supérieur au solde refusé (BalanceInsufficientException)")
    void debitAccount_insufficientBalance_shouldThrow() {
        fixture.given(createdEvent(new BigDecimal("1000.00")), activatedEvent())
                .when(new DebitAccountCommand(ACCOUNT_ID, new BigDecimal("1000.01"), CURRENCY))
                .expectException(BalanceInsufficientException.class)
                .expectExceptionMessage("Insufficient balance: 1000.00 MAD available, 1000.01 MAD requested for debit of account acc-0001");
    }

    @Test
    @DisplayName("Débit du montant exact du solde accepté")
    void debitAccount_exactBalance_shouldBeAccepted() {
        fixture.given(createdEvent(new BigDecimal("1000.00")), activatedEvent())
                .when(new DebitAccountCommand(ACCOUNT_ID, new BigDecimal("1000.00"), CURRENCY))
                // 1000.00 - 1000.00 = 0.00 (échelle 2) : comparaison par signum, pas equals (sensible à l'échelle)
                .expectState(state -> org.junit.jupiter.api.Assertions.assertEquals(
                        0, state.getBalance().signum()));
    }

    @Test
    @DisplayName("Débiter un compte BLOCKED refusé (bloqué = terminal)")
    void debitAccount_blockedAccount_shouldThrow() {
        fixture.given(
                        createdEvent(new BigDecimal("1000.00")),
                        activatedEvent(),
                        new AccountBlockedEvent(ACCOUNT_ID, AccountStatus.BLOCKED))
                .when(new DebitAccountCommand(ACCOUNT_ID, new BigDecimal("10.00"), CURRENCY))
                .expectException(InvalidAccountStatusException.class)
                .expectExceptionMessage("Account acc-0001 must be ACTIVATED to be debited (current status: BLOCKED) (a BLOCKED account is terminal and cannot receive any operation)");
    }

    // ------------------------------------------------------------------
    // Transitions de statut
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Activer un compte déjà ACTIVATED refusé")
    void activateAccount_alreadyActivated_shouldThrow() {
        fixture.given(createdEvent(new BigDecimal("1000.00")), activatedEvent())
                .when(new ActivateAccountCommand(ACCOUNT_ID))
                .expectException(InvalidAccountStatusException.class)
                .expectExceptionMessage("Account acc-0001 is already ACTIVATED");
    }

    @Test
    @DisplayName("Réactiver un compte SUSPENDED est autorisé (SUSPENDED -> ACTIVATED)")
    void activateAccount_fromSuspended_shouldEmitActivatedEvent() {
        fixture.given(
                        createdEvent(new BigDecimal("1000.00")),
                        activatedEvent(),
                        new AccountSuspendedEvent(ACCOUNT_ID, AccountStatus.SUSPENDED))
                .when(new ActivateAccountCommand(ACCOUNT_ID))
                .expectEvents(activatedEvent())
                .expectState(state -> org.junit.jupiter.api.Assertions.assertEquals(
                        AccountStatus.ACTIVATED, state.getStatus()));
    }

    @Test
    @DisplayName("Suspendre un compte ACTIVATED émet AccountSuspendedEvent")
    void suspendAccount_fromActivated_shouldEmitSuspendedEvent() {
        fixture.given(createdEvent(new BigDecimal("1000.00")), activatedEvent())
                .when(new SuspendAccountCommand(ACCOUNT_ID))
                .expectEvents(new AccountSuspendedEvent(ACCOUNT_ID, AccountStatus.SUSPENDED))
                .expectState(state -> org.junit.jupiter.api.Assertions.assertEquals(
                        AccountStatus.SUSPENDED, state.getStatus()));
    }

    @Test
    @DisplayName("Suspendre un compte déjà SUSPENDED refusé")
    void suspendAccount_alreadySuspended_shouldThrow() {
        fixture.given(
                        createdEvent(new BigDecimal("1000.00")),
                        activatedEvent(),
                        new AccountSuspendedEvent(ACCOUNT_ID, AccountStatus.SUSPENDED))
                .when(new SuspendAccountCommand(ACCOUNT_ID))
                .expectException(InvalidAccountStatusException.class)
                .expectExceptionMessage("Account acc-0001 is already SUSPENDED");
    }

    @Test
    @DisplayName("Bloquer un compte ACTIVATED émet AccountBlockedEvent")
    void blockAccount_fromActivated_shouldEmitBlockedEvent() {
        fixture.given(createdEvent(new BigDecimal("1000.00")), activatedEvent())
                .when(new BlockAccountCommand(ACCOUNT_ID))
                .expectEvents(new AccountBlockedEvent(ACCOUNT_ID, AccountStatus.BLOCKED))
                .expectState(state -> org.junit.jupiter.api.Assertions.assertEquals(
                        AccountStatus.BLOCKED, state.getStatus()));
    }

    @Test
    @DisplayName("Bloquer un compte SUSPENDED est autorisé")
    void blockAccount_fromSuspended_shouldEmitBlockedEvent() {
        fixture.given(
                        createdEvent(new BigDecimal("1000.00")),
                        activatedEvent(),
                        new AccountSuspendedEvent(ACCOUNT_ID, AccountStatus.SUSPENDED))
                .when(new BlockAccountCommand(ACCOUNT_ID))
                .expectEvents(new AccountBlockedEvent(ACCOUNT_ID, AccountStatus.BLOCKED));
    }

    @Test
    @DisplayName("Bloquer un compte déjà BLOCKED refusé")
    void blockAccount_alreadyBlocked_shouldThrow() {
        fixture.given(
                        createdEvent(new BigDecimal("1000.00")),
                        activatedEvent(),
                        new AccountBlockedEvent(ACCOUNT_ID, AccountStatus.BLOCKED))
                .when(new BlockAccountCommand(ACCOUNT_ID))
                .expectException(InvalidAccountStatusException.class)
                .expectExceptionMessage("Account acc-0001 is already BLOCKED");
    }
}
