package ma.enset.tp4cqrs.common.exceptions;

/** Débit impossible : solde insuffisant (HTTP 409). */
public class BalanceInsufficientException extends RuntimeException {
    public BalanceInsufficientException(String message) {
        super(message);
    }
}
