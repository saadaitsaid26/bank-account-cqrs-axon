package ma.enset.tp4cqrs.queries.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.enset.tp4cqrs.common.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Vue de lecture (projection) du compte bancaire. */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    private String accountId;

    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    private String currency;

    private Instant createdAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL)
    @Builder.Default
    @JsonIgnore
    private List<AccountOperation> operations = new ArrayList<>();
}
