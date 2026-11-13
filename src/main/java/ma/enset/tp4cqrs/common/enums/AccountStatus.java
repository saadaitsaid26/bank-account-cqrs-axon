package ma.enset.tp4cqrs.common.enums;

/**
 * Statut du compte bancaire.
 * Cycle de vie : CREATED -> ACTIVATED <-> SUSPENDED -> BLOCKED (terminal).
 */
public enum AccountStatus {
    CREATED,
    ACTIVATED,
    SUSPENDED,
    BLOCKED
}
