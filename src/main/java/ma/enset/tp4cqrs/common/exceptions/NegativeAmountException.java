package ma.enset.tp4cqrs.common.exceptions;

/** Montant négatif ou nul refusé. */
public class NegativeAmountException extends RuntimeException {
    public NegativeAmountException(String message) {
        super(message);
    }
}
