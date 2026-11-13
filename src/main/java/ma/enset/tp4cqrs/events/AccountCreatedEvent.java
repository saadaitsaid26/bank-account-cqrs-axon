package ma.enset.tp4cqrs.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Fait : un compte a été créé (émis par le handler de création, suivi de AccountActivatedEvent). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreatedEvent {
    private String accountId;
    private BigDecimal initialBalance;
    private String currency;
}
