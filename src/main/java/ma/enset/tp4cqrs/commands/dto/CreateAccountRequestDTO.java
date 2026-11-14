package ma.enset.tp4cqrs.commands.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Requête de création de compte. Le statut n'est pas demandé : la création auto-active le compte. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequestDTO {
    @NotBlank
    private String currency;
    @NotNull
    private BigDecimal initialBalance;
}
