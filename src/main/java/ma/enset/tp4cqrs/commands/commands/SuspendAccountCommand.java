package ma.enset.tp4cqrs.commands.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Commande de suspension d'un compte (ACTIVATED -> SUSPENDED). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuspendAccountCommand {
    @TargetAggregateIdentifier
    private String accountId;
}
