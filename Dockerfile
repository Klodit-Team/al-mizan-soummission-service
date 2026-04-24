# ═══════════════════════════════════════════════════════════════
#  Dockerfile multi-stage — soumission_service
#  Stage 1 : Build (Maven + JDK 21)
#  Stage 2 : Runtime (JRE 21 slim)
# ═══════════════════════════════════════════════════════════════

# ── Stage 1 : Build ──────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copier uniquement les fichiers nécessaires au téléchargement des dépendances
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Télécharger les dépendances (layer cache Docker)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copier le code source
COPY src src

# Compiler et packager (skip tests — exécutés dans la CI)
RUN ./mvnw package -DskipTests -B

# ── Stage 2 : Runtime ───────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Metadata
LABEL maintainer="klodit" \
      service="soumission-service" \
      version="1.0"

# Utilisateur non-root (sécurité)
RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

# Copier le JAR depuis le build stage
COPY --from=builder /app/target/*.jar app.jar

# Port exposé
EXPOSE 8004

# Health check (Actuator)
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
    CMD wget -qO- http://localhost:8004/actuator/health || exit 1

# Paramètres JVM optimisés pour conteneur
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Point d'entrée
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
# TEST AUTOMATION SCRIPT WORKING
