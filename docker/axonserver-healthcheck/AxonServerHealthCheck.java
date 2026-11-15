import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Healthcheck Axon Server — exécuté par « java » à l'intérieur du conteneur.
 *
 * Pourquoi ce fichier existe : l'image axoniq/axonserver:4.6.10 est un JRE Debian minimaliste
 * (pas de /bin/sh, pas de curl, pas de wget, pas de javac). Un healthcheck compose classique
 * (curl ou shell) est donc impossible ; ce mini-client HTTP pur est compilé sur l'hôte avec
 * « javac --release 11 » (voir scripts/verify.ps1 / README) puis monté dans le conteneur.
 *
 * Sortie : 0 si http://localhost:8024/actuator/health répond 200 avec "status":"UP", sinon 1.
 */
public class AxonServerHealthCheck {

    private static final String HEALTH_URL = "http://localhost:8024/actuator/health";

    public static void main(String[] args) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(HEALTH_URL).openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            System.exit(1);
        }
        try (InputStream in = connection.getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("\"status\":\"UP\"")) {
                System.exit(0);
            }
        }
        System.exit(1);
    }
}
