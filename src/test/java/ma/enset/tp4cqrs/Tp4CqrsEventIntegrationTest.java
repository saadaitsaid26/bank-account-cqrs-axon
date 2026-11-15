package ma.enset.tp4cqrs;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration de bout en bout (profil "test" : H2 + connecteurs Axon locaux, sans Docker).
 * Cycle complet : commande -> event store -> projection -> query -> SSE temps réel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Tp4CqrsEventIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final HttpHeaders jsonHeaders = headers();

    private static HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String createAccount(BigDecimal initialBalance, String currency) {
        Map<String, Object> body = Map.of("initialBalance", initialBalance, "currency", currency);
        ResponseEntity<String> response =
                restTemplate.exchange("/commands/accounts", HttpMethod.POST, new HttpEntity<>(body, jsonHeaders), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotBlank();
        return response.getBody().replace("\"", "");
    }

    private ResponseEntity<String> postOperation(String accountId, String operation, BigDecimal amount, String currency) {
        Map<String, Object> body = Map.of("amount", amount, "currency", currency);
        return restTemplate.exchange("/commands/accounts/" + accountId + "/" + operation,
                HttpMethod.POST, new HttpEntity<>(body, jsonHeaders), String.class);
    }

    private ResponseEntity<String> postLifecycle(String accountId, String action) {
        return restTemplate.exchange("/commands/accounts/" + accountId + "/" + action,
                HttpMethod.POST, new HttpEntity<>(jsonHeaders), String.class);
    }

    private String statement(String accountId) {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/query/accounts/" + accountId + "/statement", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    // ------------------------------------------------------------------
    // Création + event store
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Création : l'event store contient Created + Activated, la projection expose le compte")
    void createAccount_eventsAreStored_andProjectionExposesAccount() {
        String accountId = createAccount(new BigDecimal("1000.00"), "MAD");

        // L'event store contient les deux événements de création
        ResponseEntity<String> events =
                restTemplate.getForEntity("/commands/events/" + accountId, String.class);
        assertThat(events.getStatusCode().value()).isEqualTo(200);
        assertThat(events.getBody())
                .contains("AccountCreatedEvent")
                .contains("AccountActivatedEvent");

        // La projection (asynchrone) expose le compte
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<String> account =
                    restTemplate.getForEntity("/query/accounts/" + accountId, String.class);
            assertThat(account.getStatusCode().value()).isEqualTo(200);
            assertThat(account.getBody())
                    .contains("\"balance\":1000.00")
                    .contains("\"status\":\"ACTIVATED\"")
                    .contains("\"currency\":\"MAD\"");
        });
    }

    @Test
    @DisplayName("Endpoint événements d'un compte inconnu -> 404")
    void readEvents_unknownAccount_returns404() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/commands/events/" + UUID.randomUUID(), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("not found");
    }

    // ------------------------------------------------------------------
    // Opérations + relevé
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Crédit puis débit : le relevé contient les opérations et le solde est cohérent")
    void creditAndDebit_statementIsCoherent() {
        String accountId = createAccount(new BigDecimal("500.00"), "EUR");

        assertThat(postOperation(accountId, "credit", new BigDecimal("250.00"), "EUR").getStatusCode().value()).isEqualTo(200);
        assertThat(postOperation(accountId, "debit", new BigDecimal("100.00"), "EUR").getStatusCode().value()).isEqualTo(200);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            String body = statement(accountId);
            assertThat(body)
                    .contains("\"balance\":650.00")
                    .contains("\"type\":\"CREDIT\"")
                    .contains("\"type\":\"DEBIT\"");
        });
    }

    @Test
    @DisplayName("Débit supérieur au solde -> HTTP 409 (solde insuffisant)")
    void debitInsufficientBalance_returns409() {
        String accountId = createAccount(new BigDecimal("200.00"), "MAD");

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(restTemplate.getForEntity("/query/accounts/" + accountId, String.class)
                        .getStatusCode().value()).isEqualTo(200));

        ResponseEntity<String> response = postOperation(accountId, "debit", new BigDecimal("999.00"), "MAD");
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).contains("Insufficient balance");
    }

    @Test
    @DisplayName("Devise incohérente -> HTTP 400 ; activer un compte déjà activé -> HTTP 400")
    void currencyMismatch_andAlreadyActivated_return400() {
        String accountId = createAccount(new BigDecimal("100.00"), "MAD");

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(restTemplate.getForEntity("/query/accounts/" + accountId, String.class)
                        .getStatusCode().value()).isEqualTo(200));

        ResponseEntity<String> wrongCurrency = postOperation(accountId, "credit", new BigDecimal("10.00"), "USD");
        assertThat(wrongCurrency.getStatusCode().value()).isEqualTo(400);
        assertThat(wrongCurrency.getBody()).contains("Currency mismatch");

        ResponseEntity<String> alreadyActivated = postLifecycle(accountId, "activate");
        assertThat(alreadyActivated.getStatusCode().value()).isEqualTo(400);
        assertThat(alreadyActivated.getBody()).contains("already ACTIVATED");
    }

    @Test
    @DisplayName("Requête invalide : crédit sans devise -> HTTP 400 (Bean Validation) ; corps mal formé -> HTTP 400")
    void invalidOperationBody_returns400() {
        String accountId = createAccount(new BigDecimal("100.00"), "MAD");

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(restTemplate.getForEntity("/query/accounts/" + accountId, String.class)
                        .getStatusCode().value()).isEqualTo(200));

        // montant présent mais devise absente : rejeté par Bean Validation avant tout traitement métier
        ResponseEntity<String> missingCurrency = restTemplate.exchange(
                "/commands/accounts/" + accountId + "/credit",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", new BigDecimal("10.00")), jsonHeaders), String.class);
        assertThat(missingCurrency.getStatusCode().value()).isEqualTo(400);
        assertThat(missingCurrency.getBody()).contains("currency");

        // corps JSON illisible : rejeté avant désérialisation
        ResponseEntity<String> malformed = restTemplate.exchange(
                "/commands/accounts/" + accountId + "/credit",
                HttpMethod.POST, new HttpEntity<>("{amount: ", jsonHeaders), String.class);
        assertThat(malformed.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("Compte suspendu : crédit refusé (400) ; re-activation puis crédit accepté")
    void suspendedAccount_creditIsRefused_thenReactivationWorks() {
        String accountId = createAccount(new BigDecimal("300.00"), "MAD");

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(restTemplate.getForEntity("/query/accounts/" + accountId, String.class)
                        .getStatusCode().value()).isEqualTo(200));

        assertThat(postLifecycle(accountId, "suspend").getStatusCode().value()).isEqualTo(200);
        assertThat(postOperation(accountId, "credit", new BigDecimal("50.00"), "MAD").getStatusCode().value()).isEqualTo(400);

        assertThat(postLifecycle(accountId, "activate").getStatusCode().value()).isEqualTo(200);
        assertThat(postOperation(accountId, "credit", new BigDecimal("50.00"), "MAD").getStatusCode().value()).isEqualTo(200);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(statement(accountId)).contains("\"balance\":350.00"));
    }

    @Test
    @DisplayName("Query side : liste et lecture par identifiant")
    void queries_listAndById_exposeAccounts() {
        String accountId = createAccount(new BigDecimal("77.77"), "USD");

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<String> list = restTemplate.getForEntity("/query/accounts", String.class);
            assertThat(list.getStatusCode().value()).isEqualTo(200);
            assertThat(list.getBody()).contains(accountId);
        });

        ResponseEntity<String> byId = restTemplate.getForEntity("/query/accounts/" + accountId, String.class);
        assertThat(byId.getStatusCode().value()).isEqualTo(200);
        assertThat(byId.getBody()).contains("\"balance\":77.77");
    }

    // ------------------------------------------------------------------
    // SSE temps réel (watch)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Watch SSE : événement initial puis opération créditée poussée en temps réel")
    void watchEndpoint_streamsInitialResultThenCreditUpdate() throws Exception {
        String accountId = createAccount(new BigDecimal("400.00"), "MAD");

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(restTemplate.getForEntity("/query/accounts/" + accountId, String.class)
                        .getStatusCode().value()).isEqualTo(200));

        List<String> received = new CopyOnWriteArrayList<>();
        Thread reader = new Thread(() -> restTemplate.execute(
                "/query/accounts/" + accountId + "/watch",
                HttpMethod.GET,
                null,
                response -> {
                    try (BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            if (line.startsWith("data:")) {
                                received.add(line);
                                if (line.contains("\"type\":\"CREDIT\"")) {
                                    return received; // fermeture après réception de l'opération créditée
                                }
                            }
                        }
                    } catch (IOException ignored) {
                        // connexion fermée par le client ou le serveur : fin de lecture
                    }
                    return received;
                }));
        reader.setDaemon(true);
        reader.start();

        // 1) la réponse initiale du watch arrive
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> !received.isEmpty());
        // 2) l'opération créditée est poussée en temps réel via QueryUpdateEmitter
        postOperation(accountId, "credit", new BigDecimal("120.50"), "MAD");
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() ->
                received.stream().anyMatch(line -> line.contains("\"type\":\"CREDIT\"") && line.contains("120.50")));
    }

    // ------------------------------------------------------------------
    // Replay des projections
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Replay : les projections sont reconstruites depuis l'event store")
    void replayEvents_rebuildsProjections() {
        String accountId = createAccount(new BigDecimal("800.00"), "MAD");
        postOperation(accountId, "credit", new BigDecimal("200.00"), "MAD");

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(statement(accountId)).contains("\"balance\":1000.00"));

        ResponseEntity<String> replay = restTemplate.getForEntity("/query/replayEvents", String.class);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getBody()).contains("account-projection");

        // après reset + rejeu, la projection est reconstruite à l'identique
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            String body = statement(accountId);
            assertThat(body)
                    .contains("\"balance\":1000.00")
                    .contains("\"type\":\"CREDIT\"");
        });
    }

    @Test
    @DisplayName("Reset tokens (changement de backend d'event store) : projections reconstruites")
    void resetTokens_rebuildsProjections() {
        String accountId = createAccount(new BigDecimal("1500.00"), "EUR");
        postOperation(accountId, "debit", new BigDecimal("500.00"), "EUR");

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(statement(accountId)).contains("\"balance\":1000.00"));

        ResponseEntity<String> reset = restTemplate.getForEntity("/query/resetTokens", String.class);
        assertThat(reset.getStatusCode().value()).isEqualTo(200);
        assertThat(reset.getBody()).contains("account-projection");

        // les tokens sont supprimés puis le processor repart du début : projection reconstruite à l'identique
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            String body = statement(accountId);
            assertThat(body)
                    .contains("\"balance\":1000.00")
                    .contains("\"type\":\"DEBIT\"");
        });
    }
}
