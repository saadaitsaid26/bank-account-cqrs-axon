package ma.enset.tp4cqrs.queries.repositories;

import ma.enset.tp4cqrs.queries.entities.AccountOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountOperationRepository extends JpaRepository<AccountOperation, Long> {

    Optional<AccountOperation> findTop1ByAccountAccountIdOrderByTimestampDesc(String accountId);

    List<AccountOperation> findByAccountAccountIdOrderByTimestampAsc(String accountId);
}
