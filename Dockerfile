# Image runtime du TP4 : copie le fat jar produit par « .\mvnw.cmd clean package ».
# (Le build Maven reste hors Docker pour rester fidèle au déroulé du cours et éviter un build multi-stage lent.)
#
# Construction :  docker build -t tp4-cqrs-event:1.0.0 .
# Exécution :     docker run -p 8066:8066 -e SPRING_PROFILES_ACTIVE=local tp4-cqrs-event:1.0.0
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="tp4-cqrs-event" \
      org.opencontainers.image.description="TP4 - CQRS & Event Sourcing (Axon Framework, Spring Boot, Axon Server)" \
      org.opencontainers.image.ports="8066"

WORKDIR /app

# Le jar est produit par Maven avant le build Docker (voir scripts/verify.ps1)
COPY target/tp4-cqrs-event-1.0.0.jar app.jar

EXPOSE 8066

ENV JAVA_OPTS=""

# JAVA_OPTS permet par ex. de passer -Dspring.profiles.active=...
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
