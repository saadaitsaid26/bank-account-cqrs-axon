package ma.enset.tp4cqrs.queries.handlers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.tp4cqrs.common.exceptions.AccountNotFoundException;
import ma.enset.tp4cqrs.queries.dto.AccountStatementDTO;
import ma.enset.tp4cqrs.queries.entities.Account;
import ma.enset.tp4cqrs.queries.entities.AccountOperation;
import ma.enset.tp4cqrs.queries.queries.GetAllAccountsQuery;
import ma.enset.tp4cqrs.queries.queries.GetAccountByIdQuery;
import ma.enset.tp4cqrs.queries.queries.GetAccountStatementQuery;
import ma.enset.tp4cqrs.queries.queries.WatchAccountQuery;
import ma.enset.tp4cqrs.queries.repositories.AccountOperationRepository;
import ma.enset.tp4cqrs.queries.repositories.AccountRepository;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/** Query side : répond aux lectures via le bus de requêtes. */
@Component
@Slf4j
@AllArgsConstructor
public class AccountQueryHandler {

    private final AccountRepository accountRepository;
    private final AccountOperationRepository accountOperationRepository;

    @QueryHandler
    public List<Account> handle(GetAllAccountsQuery query) {
        log.info("######## Query handler: GetAllAccountsQuery");
        return accountRepository.findAll();
    }

    @QueryHandler
    public Account handle(GetAccountByIdQuery query) {
        log.info("######## Query handler: GetAccountByIdQuery {}", query.getAccountId());
        return accountRepository.findById(query.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(query.getAccountId()));
    }

    @QueryHandler
    public AccountStatementDTO handle(GetAccountStatementQuery query) {
        log.info("######## Query handler: GetAccountStatementQuery {}", query.getAccountId());
        Account account = accountRepository.findById(query.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(query.getAccountId()));
        List<AccountOperation> operations =
                accountOperationRepository.findByAccountAccountIdOrderByTimestampAsc(query.getAccountId());
        return AccountStatementDTO.builder()
                .account(account)
                .operations(operations)
                .build();
    }

    /** Réponse initiale du watch : dernière opération connue, sinon une vue vide. */
    @QueryHandler
    public AccountOperation handle(WatchAccountQuery query) {
        log.info("######## Query handler: WatchAccountQuery {}", query.getAccountId());
        return accountOperationRepository.findTop1ByAccountAccountIdOrderByTimestampDesc(query.getAccountId())
                .orElseGet(AccountOperation::new);
    }
}
