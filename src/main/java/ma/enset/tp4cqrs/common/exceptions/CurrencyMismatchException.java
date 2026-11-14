package ma.enset.tp4cqrs.common.exceptions;

/** Devise incohérente entre l'opération demandée et la devise du compte. */
public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(String message) {
        super(message);
    }
}
