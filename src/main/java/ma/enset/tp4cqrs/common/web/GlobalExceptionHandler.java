package ma.enset.tp4cqrs.common.web;

import lombok.extern.slf4j.Slf4j;
import ma.enset.tp4cqrs.common.exceptions.AccountNotFoundException;
import ma.enset.tp4cqrs.common.exceptions.BalanceInsufficientException;
import ma.enset.tp4cqrs.common.exceptions.CurrencyMismatchException;
import ma.enset.tp4cqrs.common.exceptions.InvalidAccountStatusException;
import ma.enset.tp4cqrs.common.exceptions.NegativeAmountException;
import ma.enset.tp4cqrs.common.exceptions.NegativeInitialBalanceException;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.queryhandling.QueryExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapping centralisé exceptions métier -> HTTP :
 * 400 règles simples et requêtes invalides, 409 solde insuffisant, 404 inconnu, 500 autre.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({
            NegativeInitialBalanceException.class,
            NegativeAmountException.class,
            CurrencyMismatchException.class,
            InvalidAccountStatusException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(BalanceInsufficientException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(BalanceInsufficientException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(AccountNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    /** Bean Validation (@Valid sur le corps de requête) : erreur client (400), pas serveur. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message);
    }

    /** Corps de requête illisible (JSON mal formé ou type inattendu) : erreur client (400). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    /**
     * Axon peut envelopper les exceptions des handlers dans une CommandExecutionException ;
     * on remonte la chaîne des causes pour retrouver une exception métier connue.
     */
    @ExceptionHandler(CommandExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleCommandExecution(CommandExecutionException ex) {
        Throwable business = findBusinessCause(ex);
        if (business == null) {
            log.error("Command execution failed", ex);
            return build(HttpStatus.INTERNAL_SERVER_ERROR, ex);
        }
        return mapBusiness(business, ex);
    }

    /**
     * Les queries passent par le bus de requêtes ; en mode Axon Server, l'exception métier levée par
     * le query handler est sérialisée "à distance" : seul son message survit (il n'est plus dans la
     * chaîne de causes). On retombe donc sur le message exact de AccountNotFoundException
     * ("Account %s not found") pour restituer un 404 cohérent avec le mode local.
     */
    @ExceptionHandler(QueryExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleQueryExecution(QueryExecutionException ex) {
        Throwable business = findBusinessCause(ex);
        if (business != null) {
            return mapBusiness(business, ex);
        }
        if (ex.getMessage() != null && ex.getMessage().matches("Account \\S+ not found")) {
            return build(HttpStatus.NOT_FOUND, ex);
        }
        log.error("Query execution failed", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception ex) {
        Throwable business = findBusinessCause(ex);
        if (business != null) {
            return mapBusiness(business, ex);
        }
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    private ResponseEntity<Map<String, Object>> mapBusiness(Throwable business, Exception source) {
        if (business instanceof BalanceInsufficientException e) return build(HttpStatus.CONFLICT, e);
        if (business instanceof AccountNotFoundException e) return build(HttpStatus.NOT_FOUND, e);
        if (business instanceof NegativeInitialBalanceException e) return build(HttpStatus.BAD_REQUEST, e);
        if (business instanceof NegativeAmountException e) return build(HttpStatus.BAD_REQUEST, e);
        if (business instanceof CurrencyMismatchException e) return build(HttpStatus.BAD_REQUEST, e);
        if (business instanceof InvalidAccountStatusException e) return build(HttpStatus.BAD_REQUEST, e);
        log.error("Unexpected error", source);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, source);
    }

    private Throwable findBusinessCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NegativeInitialBalanceException
                    || current instanceof NegativeAmountException
                    || current instanceof BalanceInsufficientException
                    || current instanceof InvalidAccountStatusException
                    || current instanceof CurrencyMismatchException
                    || current instanceof AccountNotFoundException) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, Exception ex) {
        return build(status, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
