package ma.enset.tp4cqrs.queries.repositories;

import ma.enset.tp4cqrs.queries.entities.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
}
