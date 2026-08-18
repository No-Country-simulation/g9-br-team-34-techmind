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
# NO USAR LA VARIANTE `-alpine` DE ESTA IMAGEN.
#
# Eclipse Temurin publica sus imagenes Alpine (musl) SOLO para linux/amd64. En
# arm64 no existen, y el build falla con:
#
#     no match for platform in manifest: not found
#
# Eso rompe dos cosas a la vez: la compilacion en cualquier Mac con Apple
# Silicon, y —lo importante— el job de CD, que construye para linux/arm64
# porque la VM Always Free de OCI es Ampere A1. Comprobado con
# `docker manifest inspect`: la variante `-jammy` si publica amd64, arm64, arm,
# ppc64le y s390x.
#
# Cuesta unos 100 MB mas que Alpine. Es el precio de que la imagen exista para
# la arquitectura a la que se despliega.
FROM eclipse-temurin:17-jre-jammy AS runtime

# Usuario sin privilegios (mismo criterio que en el ml-service).
# Estas son las opciones largas de `adduser` de Debian, que es lo que trae esta
# imagen (Ubuntu 22.04).
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

# Healthcheck contra /actuator/health, el endpoint dedicado a esto que expone
# spring-boot-starter-actuator (agregado al pom por DevOps, ver el comentario
# alli). Antes se apuntaba a /v3/api-docs para no tocar el pom, pero esa es una
# ruta funcional: en cuanto SecurityConfig la protegiera, el contenedor quedaria
# unhealthy pese a funcionar y el CD revertiria cada despliegue.
#
# CONDICION QUE DEBE MANTENERSE: /actuator/health tiene que seguir siendo
# publico. Esta anotado tambien en application.properties, que es donde lo va a
# leer quien implemente la seguridad.
#
# La imagen base (Ubuntu 22.04) trae curl, asi que no hay que instalar nada.
# `--fail` es lo que hace util al chequeo: sin el, curl devuelve 0 aunque el
# servidor conteste 401 o 503, y el contenedor se declararia sano estando roto.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl --fail --silent --max-time 4 -o /dev/null http://localhost:8080/actuator/health || exit 1

# `sh -c` es necesario para que $JAVA_OPTS se expanda; en forma exec no lo haria.
# `exec` hace que la JVM sea el PID 1, de modo que reciba directamente el SIGTERM
# de `docker stop` y pueda apagarse de forma ordenada en vez de morir por SIGKILL
# a los 10 segundos.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
