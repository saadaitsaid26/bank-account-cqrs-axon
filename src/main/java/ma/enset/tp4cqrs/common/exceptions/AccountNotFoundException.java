package ma.enset.tp4cqrs.common.exceptions;

/** Ressource (compte) introuvable côté query (HTTP 404). */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accountId) {
        super(String.format("Account %s not found", accountId));
    }
}
