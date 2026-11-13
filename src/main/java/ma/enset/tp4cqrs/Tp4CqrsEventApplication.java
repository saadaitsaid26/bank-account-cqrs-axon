package ma.enset.tp4cqrs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TP4 - CQRS & Event Sourcing avec Axon Framework, Spring Boot et Axon Server.
 * Application de gestion de comptes bancaires (command side / query side).
 */
@SpringBootApplication
public class Tp4CqrsEventApplication {

    public static void main(String[] args) {
        SpringApplication.run(Tp4CqrsEventApplication.class, args);
    }
}
