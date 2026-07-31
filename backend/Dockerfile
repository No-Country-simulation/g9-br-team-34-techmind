# API REST TechMind (Spring Boot) - imagen de produccion.
#
# NOTA PARA EL EQUIPO DE BACKEND: este archivo es un agregado de DevOps. No
# modifica el pom.xml, ni el codigo Java, ni los .properties. Se puede seguir
# trabajando con `mvnw spring-boot:run` exactamente igual que antes.
#
# Construccion en dos etapas: la etapa `builder` compila con Maven y JDK; la
# imagen final solo lleva un JRE y el .jar. Compilar y ejecutar en la misma
# imagen dejaria el JDK, Maven y el repositorio ~/.m2 en produccion, lo que
# multiplica el tamano y la superficie de ataque sin aportar nada.

# ---------------------------------------------------------------------------
# Etapa 1: compilacion
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# El pom.xml se copia SOLO, antes que el codigo fuente. Descargar las
# dependencias es el paso lento del build; al aislarlo en su propia capa, Docker
# la reutiliza en cada compilacion en la que el pom no cambio. Copiar todo junto
# volveria a bajar Spring Boot entero cada vez que alguien toca un .java.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src/ ./src/

# -DskipTests: las pruebas ya corrieron en el job de CI antes de llegar aca.
# Repetirlas dentro del build solo alarga el despliegue sin agregar informacion.
RUN mvn -B clean package -DskipTests

# El nombre del .jar depende de artifactId y version del pom. Se renombra a un
# nombre fijo para que el CMD de la etapa final no tenga que conocerlos: asi un
# cambio de version en el pom no rompe la imagen.
RUN mv target/*.jar /build/app.jar

# ---------------------------------------------------------------------------
# Etapa 2: imagen final
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# Usuario sin privilegios (mismo criterio que en el ml-service).
RUN addgroup --system --gid 1000 techmind \
    && adduser --system --uid 1000 --ingroup techmind --no-create-home techmind

WORKDIR /app

COPY --from=builder --chown=techmind:techmind /build/app.jar /app/app.jar

# Directorio de la base H2. Se crea con el dueno correcto ANTES de cambiar de
# usuario: si se creara despues, el proceso no podria escribir y el arranque
# fallaria con un error de permisos poco evidente.
#
# En el compose este directorio se monta como volumen para que los datos
# sobrevivan a un redespliegue.
RUN mkdir -p /app/data && chown -R techmind:techmind /app/data
VOLUME ["/app/data"]

USER techmind

EXPOSE 8080

# MaxRAMPercentage: la JVM debe dimensionar el heap segun la memoria asignada al
# CONTENEDOR, no segun la de la maquina anfitriona. Sin este limite, en la VM de
# OCI la JVM reservaria de mas y el kernel terminaria matando el proceso (OOM).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"

# UseSerialGC: en una instancia de 1 vCPU el recolector paralelo genera mas
# contencion que beneficio. Conviene revisarlo si se escala la VM.

# Healthcheck contra /v3/api-docs, que springdoc ya expone (esta en el pom del
# equipo). Se eligio ese endpoint para NO tener que agregar spring-boot-actuator
# al pom, ya que el codigo del backend no es alcance de DevOps.
#
# SI EL EQUIPO PROTEGE ESA RUTA en SecurityConfig, hay que actualizar este
# healthcheck o dejar /v3/api-docs publico, o el contenedor quedara marcado como
# unhealthy pese a estar funcionando.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/v3/api-docs || exit 1

# `sh -c` es necesario para que $JAVA_OPTS se expanda; en forma exec no lo haria.
# `exec` hace que la JVM sea el PID 1, de modo que reciba directamente el SIGTERM
# de `docker stop` y pueda apagarse de forma ordenada en vez de morir por SIGKILL
# a los 10 segundos.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
