package ma.enset.tp4cqrs.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Fait : le compte a été débité du montant indiqué (dans sa devise). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountDebitedEvent {
    private String accountId;
    private BigDecimal amount;
    private String currency;
}
