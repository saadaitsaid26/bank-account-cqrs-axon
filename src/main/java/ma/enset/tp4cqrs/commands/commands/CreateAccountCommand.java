package ma.enset.tp4cqrs.commands.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;

/** Commande de création d'un compte (immuable, portée par le bus de commande). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountCommand {
    @TargetAggregateIdentifier
    private String accountId;
    private BigDecimal initialBalance;
    private String currency;
}
