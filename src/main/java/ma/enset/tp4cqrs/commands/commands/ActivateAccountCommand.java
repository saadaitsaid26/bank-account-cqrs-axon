package ma.enset.tp4cqrs.commands.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Commande d'activation d'un compte (CREATED ou SUSPENDED -> ACTIVATED). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivateAccountCommand {
    @TargetAggregateIdentifier
    private String accountId;
}
