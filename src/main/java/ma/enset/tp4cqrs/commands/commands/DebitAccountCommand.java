package ma.enset.tp4cqrs.commands.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;

/** Commande de débit d'un compte. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebitAccountCommand {
    @TargetAggregateIdentifier
    private String accountId;
    private BigDecimal amount;
    private String currency;
}
