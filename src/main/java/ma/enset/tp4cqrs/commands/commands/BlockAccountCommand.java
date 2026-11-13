package ma.enset.tp4cqrs.commands.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Commande de blocage d'un compte (ACTIVATED ou SUSPENDED -> BLOCKED ; bloqué = terminal). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockAccountCommand {
    @TargetAggregateIdentifier
    private String accountId;
}
