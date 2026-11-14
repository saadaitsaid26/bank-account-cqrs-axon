package ma.enset.tp4cqrs.common.exceptions;

/** Solde initial négatif refusé (règle du cours : NegativeInitialBalanceException). */
public class NegativeInitialBalanceException extends RuntimeException {
    public NegativeInitialBalanceException(String message) {
        super(message);
    }
}
