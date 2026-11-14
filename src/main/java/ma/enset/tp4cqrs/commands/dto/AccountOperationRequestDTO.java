package ma.enset.tp4cqrs.commands.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Requête de crédit / débit d'un compte. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountOperationRequestDTO {
    @NotNull
    private BigDecimal amount;
    @NotBlank
    private String currency;
}
