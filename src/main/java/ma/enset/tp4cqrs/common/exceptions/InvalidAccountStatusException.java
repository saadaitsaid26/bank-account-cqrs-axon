package ma.enset.tp4cqrs.common.exceptions;

/** Transition de statut invalide (compte déjà activé/suspendu/bloqué, compte bloqué = terminal...). */
public class InvalidAccountStatusException extends RuntimeException {
    public InvalidAccountStatusException(String message) {
        super(message);
    }
}
