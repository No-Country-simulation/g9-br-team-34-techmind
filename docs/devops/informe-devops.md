# Informe DevOps — TechMind

**Proyecto:** G9-LATAM-Team-34-TechMind
**Rama:** `feature/deploy-oci`
**Alcance:** infraestructura, contenedores, integración y entrega continua, despliegue en Oracle Cloud Infrastructure.
**Entorno documentado:** macOS con Apple Silicon (arm64), zsh.

> **Sobre el sistema operativo.** Los comandos de este documento están en sintaxis Unix (macOS/Linux). Si alguien del equipo trabaja en Windows, los equivalentes son `%USERPROFILE%` en vez de `~`, e `icacls` en vez de `chmod`. Vale aclarar algo que además es un argumento a favor del diseño: **nada de lo que corre de verdad depende de tu máquina.** Los contenedores corren sobre Linux dentro de Docker, CI/CD corre en runners Ubuntu de GitHub, y producción es Oracle Linux ARM. Tu equipo solo se usa para crear recursos y cargar credenciales.

Este documento cuenta dos cosas: **qué se construyó y por qué**, y **qué falta para que esté publicado en internet**. Está escrito para que alguien que no participó pueda entender las decisiones, y para que vos puedas defenderlas frente a un jurado sin depender de la memoria.

> **Qué entra y qué no en este informe.** Acá se documenta únicamente el trabajo de DevOps. El código de negocio del backend Java (controladores, servicios, entidades, seguridad) y el modelo definitivo de Ciencia de Datos son de otros integrantes del equipo y **no forman parte de este alcance**. Se los menciona solo en los dos puntos donde tocan la infraestructura, y está señalado explícitamente.

---

## 1. Resumen en una página

TechMind son dos servicios que tienen que hablarse: una API REST en Java que atiende al mundo, y un servicio de Python que sirve un modelo de clasificación de texto. El trabajo de DevOps fue conseguir que esos dos servicios se levanten con **un solo comando** en cualquier máquina, y que lleguen a producción **solos** cada vez que alguien mergea a `main`.

Al día de hoy:

- `make up` levanta el sistema completo en local. Verificado, funcionando.
- Cada PR dispara pruebas, construcción de imágenes y una prueba de humo extremo a extremo.
- Cada merge a `main` entrena el modelo, lo publica, construye las imágenes para ARM, las sube al registro de Oracle y actualiza los contenedores en la VM por SSH. Si algo no responde, **revierte solo**.
- El servicio de inferencia está terminado y probado.
- La infraestructura de OCI **todavía no existe**: hay que crearla, y es lo único que separa al proyecto de estar publicado.

Son unas 4.000 líneas entre configuración, código de infraestructura y documentación.

---

## 2. La arquitectura, y por qué es así

```
        Cliente (navegador, Postman, otra app)
                    │  HTTP :8080
                    ▼
        ┌───────────────────────────────┐
        │   OCI Compute — VM Ampere A1  │
        │                               │
        │   ┌────────────┐              │
        │   │  backend   │ :8080 ───────┼──► público
        │   │ Spring Boot│              │
        │   └─────┬──────┘              │
        │         │ red privada         │
        │         ▼                     │
        │   ┌────────────┐              │
        │   │ ml-service │ :8000        │  ← NO publicado
        │   │  FastAPI   │              │
        │   └─────┬──────┘              │
        └─────────┼─────────────────────┘
                  │ instance_principal
                  ▼
        OCI Object Storage — model.joblib
```

Tres decisiones estructurales explican casi todo lo demás:

**El ml-service no se expone.** En producción no publica ningún puerto. Solo el backend puede alcanzarlo, por la red interna de Docker. Si lo publicáramos, cualquiera en internet podría consultar el modelo directamente, sin pasar por ninguna autenticación. Es una decisión de seguridad, no de comodidad, y por eso hay una comprobación automática en CI que falla si alguien la revierte.

**En la VM no se compila nada.** Lo que se despliega son imágenes ya construidas y probadas en GitHub Actions. Si compiláramos en el servidor, el despliegue dependería de la red y del CPU de una máquina chica, y la imagen que corre en producción no sería bit a bit la que se probó. Además, un build fallido dejaría el servidor a medias.

**La VM no guarda ningún secreto de OCI.** Usa `instance_principal`: la instancia se autentica ante Oracle con su propia identidad, mediante un *Dynamic Group* y una *Policy*. No hay claves privadas en disco que rotar ni que se puedan filtrar. Es la práctica correcta y, además, una que se nota.

---

## 3. Qué hace cada archivo

### 3.1 Contenedores

**`backend/Dockerfile`** (108 líneas) — Construcción en dos etapas. La primera compila con Maven y JDK; la segunda se queda solo con un JRE y el `.jar`. Compilar y ejecutar en la misma imagen dejaría el JDK, Maven y el repositorio `~/.m2` dentro de producción: más peso y más superficie de ataque, a cambio de nada.

El `pom.xml` se copia **solo**, antes que el código fuente. Descargar dependencias es el paso lento; al aislarlo en su propia capa, Docker la reutiliza mientras el `pom` no cambie. Copiar todo junto volvería a bajar Spring Boot entero cada vez que alguien toca un `.java`.

Corre como usuario sin privilegios (uid 1000). Tiene `HEALTHCHECK` contra `/actuator/health`. La JVM arranca con `-XX:MaxRAMPercentage=75` para dimensionar el heap según la memoria del **contenedor** y no la de la máquina: sin eso, en una VM chica el kernel termina matando el proceso.

**`ml-service/Dockerfile`** (87 líneas) — Misma idea. La etapa `builder` instala dependencias y **entrena el modelo**; la final se queda con el entorno virtual, el código y el artefacto. Se entrena en tiempo de build y no de arranque a propósito: el arranque debe ser rápido y determinista, y dos réplicas del mismo servicio tienen que servir exactamente el mismo modelo. Entrenar al arrancar rompe las dos cosas.

El healthcheck usa `urllib` de Python porque la imagen `slim` no trae `curl` ni `wget`, e instalarlos solo para eso agregaría paquetes a producción.

**Los dos `.dockerignore`** — Uno por servicio, porque cada uno tiene su propio contexto de build. El del backend excluye `target/`; el del ml-service excluye `.venv/` y `models/`. No es cosmético: el contexto se transfiere entero al daemon antes de empezar, y un `.venv` local puede pesar más que la imagen resultante.

### 3.2 Orquestación

**`docker-compose.yml`** (99 líneas) — Desarrollo local. Levanta ambos servicios en una red privada. El backend depende del ml-service con `condition: service_healthy`, no con `service_started`: el modelo tarda unos segundos en cargar, y arrancar el backend antes produciría fallos de conexión en las primeras peticiones. La base H2 vive en un volumen; sin eso, cada `docker compose down` borraría los datos.

**`docker-compose.prod.yml`** (117 líneas) — Sobrescribe al anterior en vez de reemplazarlo, para que la topología de servicios esté definida **en un solo lugar** y no pueda divergir entre entornos. Diferencias: imágenes del registro en vez de build local, ml-service sin puertos, modelo desde Object Storage, límites de memoria y rotación de logs.

Sobre la rotación: sin ella los logs crecen hasta llenar el disco y tumban el servidor entero. Es una de las formas más comunes de perder una VM pequeña, y pasa siempre de madrugada.

**`.env.example`** (62 líneas) — Plantilla. El `.env` real nunca se versiona.

**`Makefile`** (133 líneas) — Para que nadie tenga que recordar la invocación exacta de `docker compose` con dos `-f` y siete variables. `make` a secas lista todo.

### 3.3 Integración y entrega continua

**`.github/workflows/ci.yml`** (320 líneas) — Corre en cada push y cada PR. Cuatro trabajos:

1. **Backend** — compila y prueba con Maven, publica el `.jar`.
2. **ml-service** — lint con ruff, entrena, ejecuta pytest, publica las métricas del modelo como artefacto descargable. Así cualquiera puede responder "¿mejoró o empeoró el modelo con este cambio?" sin reentrenar.
3. **Imágenes** — verifica que los Dockerfiles funcionen, valida **ambos** archivos de compose, comprueba que el ml-service no publique puertos en producción, y valida que todas las imágenes base existan para la arquitectura de despliegue.
4. **Prueba de humo** — levanta el sistema completo y lo interroga con los tres ejemplos del brief. Es documentación que no puede quedar desactualizada: si dejara de funcionar, CI se pone rojo.

Los tres primeros corren en paralelo. El de imágenes espera, porque construir un contenedor con código que no compila es tiempo desperdiciado.

**`.github/workflows/cd.yml`** (373 líneas) — Corre al mergear a `main`, o a mano indicando una etiqueta. Cinco pasos:

1. **Verificar** — repite las pruebas. Es a propósito: GitHub no encadena CI con CD por el solo hecho de que ambos escuchen `main`. Sin este trabajo, un merge con las pruebas en rojo llegaría igual a producción. Cuesta dos o tres minutos de runner; desplegar una versión rota a mitad de la demo cuesta bastante más.
2. **Publicar modelo** — entrena y sube `model.joblib` a Object Storage, dos veces: con nombre versionado por commit, como historial inmutable, y con nombre fijo, que es el que lee el servicio.
3. **Publicar imágenes** — construye para **ARM** y sube al registro de Oracle. Necesita QEMU porque el runner es x86 y la VM es Ampere.
4. **Desplegar** — copia los compose por SCP, hace `pull` y `up --wait` por SSH.
5. **Verificar desde afuera** — consulta la API **desde el runner**, no por SSH. Eso confirma que el servicio es accesible a través del firewall de la red y del sistema operativo, no solo desde dentro de la propia máquina.

Si los healthchecks no pasan, **revierte automáticamente** a la etiqueta anterior. La etiqueta de imagen es el SHA del commit y nunca `latest`: `latest` te dice cuál es la última, pero no qué código está corriendo ni cómo volver atrás.

### 3.4 Scripts

**`scripts/provision-vm.sh`** (226 líneas) — Deja la VM lista: instala Docker, valida que Compose sea ≥ 2.24, agrega el usuario al grupo `docker`, abre el 8080 en el firewall del sistema operativo, crea `/opt/techmind` y configura rotación de logs global. Es idempotente, porque la primera ejecución casi nunca sale perfecta.

Detalle que ahorra una tarde: OCI tiene **dos** capas de firewall —la de la red virtual y la del sistema operativo— y hay que abrir las dos. Olvidar la segunda es la causa número uno de "el contenedor corre pero no puedo entrar".

**`scripts/smoke-test.sh`** (325 líneas) — Ejecuta los tres ejemplos del brief contra un sistema levantado. Detecta con qué topología está hablando: si el puerto 8000 responde, prueba el modelo directamente; si no responde pero la API sí, entiende que está en producción y verifica todo a través de la API pública.

Distingue entre "todavía no implementado" y "roto": un 404 en el endpoint del backend se reporta como pendiente, un 500 se reporta como fallo. Esa distinción es lo que permite usar el script desde el primer día sin que el equipo de backend haya escrito nada.

**`scripts/configurar-github.sh`** (220 líneas) — Carga los catorce secrets y variables y crea el environment. Las claves privadas se leen **desde el archivo**, no pegadas en la terminal: pegar una clave multilínea es la forma más común de que llegue cortada, y el error que produce después no menciona la clave por ningún lado.

### 3.5 El servicio de inferencia

`ml-service/` es un servicio FastAPI completo: `/health` y `/predict`, configuración por variables de entorno, carga del modelo desde disco o desde Object Storage, y pruebas de contrato.

Vale aclarar el alcance: es un **andamio de DevOps**, no el entregable de Ciencia de Datos. Existe para que la canalización completa —entrenar, serializar, publicar, servir en un contenedor— se pueda demostrar de punta a punta desde el primer día, sin quedar bloqueada esperando al notebook. El equipo de Ciencia de Datos puede reemplazar el dataset y el script de entrenamiento; lo único que debe respetarse es el formato del artefacto serializado, porque es lo que `app/model.py` sabe leer.

Detalles que importan:

- Si el modelo no carga, el servicio **no se muere**. Queda arriba, marcado como no sano, y `/health` responde con la causa exacta. Si el proceso muriera, Docker lo reiniciaría en bucle y el motivo real quedaría enterrado entre reinicios.
- La descarga desde Object Storage escribe a un archivo temporal y después renombra. Si se corta a la mitad, lo que queda es un `.tmp` incompleto y no un `model.joblib` corrupto que el próximo arranque intentaría cargar.
- Si Object Storage no responde pero el volumen conserva una copia previa, el servicio arranca con ella y lo reporta como `local-fallback` en `/health`. Un incidente de Oracle no debería dejar la API caída cuando el artefacto ya está en disco.
- Las pruebas verifican el **contrato**, no la calidad del modelo. Afirmar que "Tutorial de Docker" es DevOps haría que la suite fallara cada vez que Ciencia de Datos reentrena, que es justo cuando no querés que el pipeline se detenga.

El dataset semilla tiene 56 documentos en siete categorías: Backend, Bases de Datos, Ciencia de Datos, DevOps, Frontend, Móviles, Seguridad.

### 3.6 Documentación

**`docs/devops/despliegue-oci.md`** (732 líneas) — Guía completa: recursos de OCI paso a paso, identidad y permisos, secrets, primer despliegue, runbook de operación diaria, tabla de diagnóstico de fallos y apéndices.

---

## 4. Problemas reales que aparecieron, y cómo

Esta sección existe porque es la parte más honesta del informe: **casi nada de esto se había ejecutado nunca**. El workflow de CI dispara en `main` y `develop`, y todo el trabajo estaba en una rama de feature sin PR abierto. Cuando por fin se ejecutó, aparecieron tres cosas.

### La imagen base del backend no existe para ARM

```
FROM eclipse-temurin:17-jre-alpine
ERROR: no match for platform in manifest: not found
```

Eclipse Temurin publica sus imágenes Alpine **solo para x86_64**. En ARM no existen.

Esto no era un problema local: el trabajo de CD construye para `linux/arm64` porque la VM Always Free es Ampere A1. **El despliegue del backend en OCI estaba roto de raíz** y nadie lo sabía, porque esa imagen nunca se había construido para esa arquitectura. Habría fallado en el primer despliegue real, en el peor momento posible.

Se cambió a `eclipse-temurin:17-jre-jammy`, verificado con `docker manifest inspect` (publica amd64, arm64, arm, ppc64le y s390x). Cuesta unos 100 MB más; es el precio de que la imagen exista para la arquitectura a la que se despliega.

Y para que no vuelva a pasar, se agregó una comprobación en CI que valida los manifiestos de **todas** las imágenes base contra la plataforma de despliegue, en cada PR. Hacía falta porque CI construye para amd64 —la arquitectura del runner— y sin esa comprobación un `FROM` incompatible pasa en verde hasta el despliegue.

### Quince violaciones de lint que habrían puesto la canalización en rojo

`ruff` encontró quince problemas en el ml-service: `typing.List` en vez de `list`, `Optional[X]` en vez de `X | None`, `zip()` sin `strict=`, `timezone.utc` en vez de `UTC`. Nada grave en sí, pero el paso de lint está en **CI y en CD**, así que el primer PR habría quedado rojo y ningún despliegue habría llegado a ejecutarse.

Corregidas —diecinueve en total contando las de importación—, verificadas con el mismo comando que corre CI.

### El healthcheck del backend era una bomba de tiempo

Apuntaba a `/v3/api-docs`, una ruta funcional de Swagger. Funciona hoy, pero el equipo de backend tiene pendiente implementar `SecurityConfig` y `AuthenticationFilter`. El día que protejan esa ruta, el healthcheck recibiría un 401, el contenedor quedaría marcado como no sano **pese a funcionar perfectamente**, y el despliegue se revertiría solo. En cada intento.

Se agregó `spring-boot-actuator` y el healthcheck pasó a `/actuator/health`, un endpoint dedicado a esto. Solo se expone `health`; el resto de endpoints de actuator quedan apagados porque filtran configuración interna. El indicador de disco también se apagó: marca caído cuando quedan menos de 10 MB libres, y en una VM chica eso convertiría un disco lleno en un rollback automático, cuando lo que hace falta es liberar espacio.

Queda **una condición que hay que comunicarle al equipo de backend**: `/actuator/health` tiene que quedar en `permitAll()`. Está anotado en `application.properties`, que es donde lo va a leer quien implemente la seguridad.

### Cuatro vulnerabilidades críticas en el Tomcat embebido

Al agregar el escaneo de imágenes, Trivy encontró cuatro CVE **críticas y con parche publicado** en el Tomcat que trae Spring Boot 3.3.0 (versión 10.1.24):

| CVE | Qué permite |
|---|---|
| `CVE-2025-24813` | ejecución remota de código por subida de archivos |
| `CVE-2026-41293` | cabeceras HTTP/2 sin validar |
| `CVE-2026-43512` | omisión de autenticación digest |
| `CVE-2026-43515` | autorización incorrecta |

Lo interesante es que **actualizar Spring Boot no las resuelve**. Consultando el BOM de la 3.3.13, la 3.4.7 y la 3.5.3, las tres siguen fijando Tomcat 10.1.42; el parche está en la 10.1.55.

La solución fue sobrescribir una sola propiedad en el `pom.xml`:

```xml
<tomcat.version>10.1.55</tomcat.version>
```

Es lo más acotado posible: mantiene la versión de Spring Boot que eligió el equipo y solo mueve Tomcat dentro de su misma línea `10.1.x`, compatible a nivel de API. Verificado: `mvn verify` pasa, la imagen reconstruida reporta `tomcat-embed-core 10.1.55`, y Trivy vuelve a salir en verde.

Sin el escaneo, esto habría llegado a producción sin que nadie lo supiera.

### El proxy de HTTPS rompía el despliegue normal

Al agregar Caddy detrás de un `profile` de Compose, la variable del dominio se declaró con la sintaxis `${VAR:?mensaje}` que usa el resto del archivo para las obligatorias.

No funciona: **Compose interpola el archivo completo antes de filtrar por perfil**, así que exigía el dominio incluso con HTTPS apagado. Rompía el despliegue normal y también el paso de validación de CI. Se cambió por un valor por defecto, y se detectó únicamente porque el compose de producción ahora se valida.

### Además

- `spring.jpa.hibernate.ddl-auto=validate` en producción exige que las tablas ya existan, pero la base H2 arranca vacía. En cuanto el equipo anote `@Entity`, Hibernate no encontraría la tabla y el despliegue se revertiría. Cambiado a `update`.
- El compose de producción nunca se validaba en CI. Un error de sintaxis solo se habría descubierto en la VM, a mitad de un despliegue. Ahora se valida fusionado con el base, que es la única forma en que se usa de verdad.
- El smoke test daba cuatro fallos garantizados contra producción, porque comprobaba un puerto que **debe** estar cerrado. Ahora detecta la topología.
- `provision-vm.sh` solo avisaba si Compose era anterior a 2.24. Ahora aborta: con una versión vieja el compose de producción ni siquiera parsea, así que no tiene sentido dejar la máquina aparentemente lista.

---

## 5. Qué está verificado, y cómo

Todo lo siguiente se ejecutó de verdad, no se leyó:

| Verificación | Resultado |
|---|---|
| Construcción de ambas imágenes | backend 479 MB · ml-service 1,25 GB |
| Sistema levantado con healthchecks | ambos contenedores `healthy` |
| Backend: `mvn verify` | BUILD SUCCESS, 1/1 prueba |
| ml-service: `ruff` | sin violaciones |
| ml-service: entrenamiento | modelo serializado, 43,5 KB |
| ml-service: `pytest` | 9/9 pruebas |
| Compose de producción fusionado | resuelve; ml-service 0 puertos, backend 1 puerto |
| Usuarios de los contenedores | `uid=1000`, sin privilegios, ambos |
| Prueba de humo local | los 3 ejemplos del brief clasifican correcto |
| Prueba de humo en modo producción | 0 fallos (antes: 4 falsos garantizados) |
| Manifiestos de imágenes base | las 3 publican arm64 |

---

# 6. LO QUE FALTA

Nada de lo que falta está en el repositorio. **El código y la configuración están terminados.** Lo que falta es crear la infraestructura y cargar las credenciales.

## 6.1 En tu máquina

**Docker ya está instalado y funcionando, y GitHub Desktop también.** Con eso alcanza: no falta nada obligatorio.

Vale aclarar un punto que es fácil de confundir. El `git` de `/usr/bin` está bloqueado porque no se aceptó la licencia de Xcode, pero **eso no impide trabajar**: GitHub Desktop trae su propio git embebido (2.53.0, en `Contents/Resources/app/git/`) que no depende de Xcode para nada. Commit, push, branches y PR se hacen desde ahí sin problema.

Lo único que el git roto sí afecta:

| Qué | Impacto |
|---|---|
| Panel de Source Control de VS Code | no funciona (usa el git del sistema) |
| `git` desde la terminal | no funciona |
| Detección automática del repo en `gh` | resuelto: el script lee `.git/config` directamente |

Si en algún momento querés cualquiera de esas dos primeras, es un comando:

```bash
sudo xcodebuild -license accept
```

Pero **no es un requisito para desplegar**.

Opcional y recomendado:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
brew install gh oci-cli jq
```

**No necesitás JDK, Maven ni Python.** Todo compila dentro de contenedores; ya está comprobado.

## 6.2 Las llaves: qué es cada una y para qué sirve

Acá se confunde mucha gente, así que vale la pena separarlo bien. Hay **tres identidades distintas**, con tres propósitos distintos, y una clave SSH aparte.

| Identidad | Quién la usa | Para qué | Cómo se obtiene |
|---|---|---|---|
| **Instance Principal** | la VM | descargar el modelo del bucket | Dynamic Group + Policy. **No genera ninguna clave** |
| **API key de OCI** | GitHub Actions | subir el modelo al bucket | Consola → Users → API Keys → Generate |
| **Auth Token** | GitHub Actions y la VM | login en el registro de imágenes | Consola → Users → Auth Tokens → Generate |
| **Clave SSH** | GitHub Actions | entrar a la VM a desplegar | `ssh-keygen` en tu máquina |

Tres advertencias que valen oro:

- **El Auth Token no es la contraseña de tu cuenta de Oracle.** Es un token aparte, y se genera en una pantalla distinta.
- **El Auth Token se muestra una sola vez.** Copialo en el momento o hay que generar otro.
- **La clave SSH del despliegue tiene que ser nueva**, no tu clave personal. La privada va a vivir dentro de un secret de GitHub.

Generá la SSH ahora, porque la vas a necesitar antes de crear la instancia:

```bash
ssh-keygen -t ed25519 -C "techmind-deploy" -f ~/.ssh/techmind_deploy -N ""
cat ~/.ssh/techmind_deploy.pub
```

## 6.3 Los recursos de OCI

Ninguno existe todavía. Hay **dos caminos** para crearlos y hacen exactamente lo mismo.

### Camino A — Terraform (recomendado)

Toda la infraestructura está descrita en [`infra/terraform/`](../../infra/terraform/): red, Internet Gateway, tabla de rutas, subred pública, Security List, instancia A1, bucket privado, Dynamic Group y Policy.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
# completar terraform.tfvars

terraform init
terraform plan     # mirá qué va a crear ANTES de crearlo
terraform apply
```

No hace falta instalar Terraform: el [README de esa carpeta](../../infra/terraform/README.md) trae la variante que corre en contenedor.

Dos ventajas concretas más allá de la comodidad:

- **Terraform imprime al final las siete variables de GitHub ya resueltas**, con tu IP y tu namespace puestos. Se acabó copiarlas a mano desde cinco pantallas distintas de la consola, que es donde se cuela la mayoría de los errores de configuración.
- **Ante un `Out of host capacity` de Ampere**, cambiar de Availability Domain es editar una línea y reaplicar, en vez de rehacer el formulario entero.

Lo único que Terraform **no** puede crearse a sí mismo son las credenciales: hacen falta la clave SSH y una API key antes de empezar. Es el huevo antes de la gallina, y está explicado en el README de la carpeta.

Verificado: `terraform fmt`, `init` y `validate` pasan limpio con el proveedor `oracle/oci 6.37.0` fijado en el lock file.

### Camino B — Consola web, paso a paso

Sigue siendo válido, y conviene leerlo aunque uses Terraform: explica **qué es** cada recurso y por qué está. Si algo falla después, es esta sección la que te dice dónde mirar.

El orden importa: cada paso depende del anterior.

### Paso 1 — Anotar los identificadores

Consola → menú de perfil (arriba a la derecha) → **Tenancy**.

Anotá el **Object Storage Namespace** y tu **región**. Después, en *Identity & Security → Compartments*, anotá el **OCID del compartment** (podés usar el raíz, el que lleva el nombre de tu tenancy).

El registro de contenedores sale de la región:

| Región | Registro |
|---|---|
| São Paulo | `gru.ocir.io` |
| Santiago | `scl.ocir.io` |
| Bogotá | `bog.ocir.io` |
| Ashburn | `iad.ocir.io` |

### Paso 2 — La red

**Networking → Virtual Cloud Networks → Start VCN Wizard → Create VCN with Internet Connectivity**

Nombre `techmind-vcn`, CIDR `10.0.0.0/16`, subred pública `10.0.0.0/24`. El asistente crea la red, la subred, el Internet Gateway y las rutas de una sola vez. Hacerlo a mano es donde más gente se traba.

### Paso 3 — Abrir el puerto 8080

Tu VCN → **Security Lists** → `Default Security List` → **Add Ingress Rules**:

```
Stateless: No
Source CIDR: 0.0.0.0/0
IP Protocol: TCP
Destination Port Range: 8080
```

Si te salteás este paso, todo lo demás va a funcionar y no vas a poder entrar desde afuera. Es literalmente el error más común de todo el proceso.

### Paso 4 — La máquina virtual

**Compute → Instances → Create Instance**

| Campo | Valor |
|---|---|
| Image | Oracle Linux 9 |
| Shape | `VM.Standard.A1.Flex` — 2 OCPU / 12 GB |
| Subnet | la **pública** de `techmind-vcn` |
| Assign public IPv4 | Sí |
| SSH keys | pegar el contenido de `~/.ssh/techmind_deploy.pub` |

**Este es el paso que puede fallar y no depende de vos.** El shape Ampere A1 sufre `Out of host capacity` de forma crónica. Si te lo rechaza: probá otro Availability Domain, probá a otra hora, o pasá al plan B del apéndice del runbook. **Hacé este paso el primer día**, no la semana de la entrega.

Anotá la **IP pública**. Verificá el acceso antes de seguir:

```bash
ssh -i ~/.ssh/techmind_deploy opc@<IP>
```

### Paso 5 — El bucket

**Storage → Buckets → Create Bucket** → nombre `techmind-models`, visibilidad **Private**. Dejalo privado: el acceso se resuelve con identidad, no exponiendo el bucket.

### Paso 6 — Que la VM pueda leer el bucket sin secretos

**Identity & Security → Domains → Default domain → Dynamic Groups → Create**

- Nombre: `techmind-instances`
- Regla: `ALL {instance.compartment.id = '<OCID del compartment>'}`

Después, **Identity & Security → Policies → Create Policy → Show manual editor**:

```
Allow dynamic-group techmind-instances to read objects in compartment id <OCID> where target.bucket.name = 'techmind-models'
```

Si tu tenancy usa Identity Domains, el grupo se escribe `'Default'/'techmind-instances'`.

Solo `read`. La VM nunca debe poder escribir en el bucket: quien publica modelos es la canalización, no el servidor.

### Paso 7 — La API key

Mismo dominio → **Users** → tu usuario → **API Keys → Add API Key → Generate API Key Pair** → descargá la clave privada.

OCI te muestra un bloque de configuración. De ahí salen tres valores: `user`, `tenancy` y `fingerprint`. El `.pem` descargado es el cuarto.

### Paso 8 — El Auth Token

Mismo usuario → **Auth Tokens → Generate Token**, descripción `techmind-ocir`. **Copialo en el momento.**

El usuario del registro es `<namespace>/<tu-usuario>`, o `<namespace>/Default/<tu-usuario>` si hay Identity Domains. Verificalo antes de confiar en él:

```bash
docker login gru.ocir.io -u '<namespace>/<usuario>'
```

Si falla, casi siempre es que falta el prefijo del dominio.

### Paso 9 — Preparar la VM

```bash
scp -i ~/.ssh/techmind_deploy scripts/provision-vm.sh opc@<IP>:~
ssh -i ~/.ssh/techmind_deploy opc@<IP> 'bash provision-vm.sh'
```

Cerrá y reabrí la sesión SSH al terminar, para que el usuario tome el grupo `docker`.

### Paso 10 — El archivo de configuración de la VM

```bash
ssh -i ~/.ssh/techmind_deploy opc@<IP>
vi /opt/techmind/.env
```

Generá las credenciales así:

```bash
openssl rand -base64 24    # DB_PASSWORD
openssl rand -hex 32       # TECHMIND_API_KEY
```

**Una advertencia que te puede costar una hora:** H2 crea la base con esa contraseña en el primer arranque. Si la cambiás después, el backend falla con `Wrong user name or password` y el único arreglo es borrar el volumen, perdiendo los datos. Elegila una vez y no la toques.

## 6.4 Las variables: dónde va cada cosa

Son doce valores, y viven en dos lugares distintos. Ninguno se versiona.

### Los siete secrets de GitHub

| Secret | Qué es |
|---|---|
| `OCI_CLI_USER` | OCID de tu usuario (`ocid1.user.oc1..`) |
| `OCI_CLI_TENANCY` | OCID del tenancy (`ocid1.tenancy.oc1..`) |
| `OCI_CLI_FINGERPRINT` | huella de la API key (`a1:b2:c3:...`) |
| `OCI_CLI_KEY_CONTENT` | el `.pem` de la API key, **completo** |
| `OCIR_USERNAME` | `<namespace>/<usuario>` |
| `OCIR_AUTH_TOKEN` | el Auth Token |
| `OCI_SSH_PRIVATE_KEY` | `~/.ssh/techmind_deploy`, **sin** el `.pub` |

### Las siete variables de GitHub

| Variable | Ejemplo |
|---|---|
| `OCI_REGION` | `sa-saopaulo-1` |
| `OCI_NAMESPACE` | `axxxxxxxxxxx` |
| `OCI_BUCKET_NAME` | `techmind-models` |
| `OCIR_REGISTRY` | `gru.ocir.io` |
| `OCI_HOST` | la IP pública |
| `OCI_SSH_USER` | `opc` |
| `TARGET_PLATFORM` | `linux/arm64` |

### El environment

Hay que **crear** un environment llamado `produccion` en *Settings → Environments*. El workflow lo referencia pero no lo crea, y si falta, el trabajo de despliegue no arranca.

### Todo eso, en un comando

En vez de catorce formularios web:

```bash
brew install gh
gh auth login
./scripts/configurar-github.sh
```

Va pidiendo valor por valor con el formato de ejemplo al lado, lee los secretos sin mostrarlos en pantalla, toma las claves privadas desde el archivo, crea el environment y al final verifica que no falte ninguno. Si dejás un campo vacío no lo toca, así que podés correrlo varias veces a medida que conseguís los valores.

### Las doce variables del `.env` de la VM

`OCIR_REGISTRY` · `OCI_NAMESPACE` · `OCI_REGION` · `OCI_BUCKET_NAME` · `OCI_MODEL_OBJECT` · `IMAGE_TAG` · `BACKEND_PORT` · `DB_USERNAME` · `DB_PASSWORD` · `TECHMIND_API_KEY` · `CORS_ALLOWED_ORIGINS` · `INFERENCE_SERVICE_TIMEOUT_MS`

`IMAGE_TAG` la actualiza el workflow en cada despliegue; el resto se completa a mano una sola vez.

## 6.5 ¿Hace falta un dominio?

**No es obligatorio.** Al terminar los pasos anteriores vas a tener esto, público y funcionando:

```
http://<IP>:8080/swagger-ui/index.html
http://<IP>:8080/actuator/health
http://<IP>:8080/api/v1/contenidos
```

Cualquiera con esa dirección entra. Está en internet y se actualiza solo con cada merge a `main`.

Ahora, conviene entender qué **no** te da Oracle. Si venís de Netlify o Vercel, la diferencia es grande: esas son plataformas que compilan, hostean, te dan un subdominio y HTTPS sin que hagas nada. OCI Compute te da **una máquina virtual vacía** y ahí termina su trabajo. Todo lo demás —instalar Docker, abrir puertos, compilar, publicar, revertir— es exactamente lo que construimos en este repositorio. Esa es la diferencia entre usar una plataforma y hacer DevOps.

Vale aclarar que Netlify tampoco podría hostear este proyecto: es una JVM corriendo permanentemente, un servicio Python con scikit-learn cargado en memoria y una base con estado. Netlify sirve para frontends estáticos y funciones. No elegiste el camino difícil pudiendo usar el fácil.

### Si querés HTTPS y una URL presentable

**Ya está construido, solo hay que encenderlo.** No hace falta comprar dominio.

Dos piezas: **`sslip.io`**, un servicio gratuito que convierte cualquier IP en un dominio (si tu IP es `140.238.1.2`, el nombre `techmind.140-238-1-2.sslip.io` ya resuelve ahí sin registrar nada), y **Caddy** como proxy inverso, que pide el certificado de Let's Encrypt solo, lo renueva solo y redirige HTTP a HTTPS.

Tres pasos:

```bash
# 1. Abrir 80 y 443 en la Security List
cd infra/terraform
terraform apply -var 'habilitar_https=true'

# 2. Dos líneas en el .env de la VM
ssh -i ~/.ssh/techmind_deploy opc@<IP>
cat >> /opt/techmind/.env <<'EOF'
COMPOSE_PROFILES=https
TECHMIND_DOMINIO=techmind.140-238-1-2.sslip.io
EOF

# 3. Redesplegar
cd /opt/techmind
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --wait
```

Y queda `https://techmind.140-238-1-2.sslip.io/swagger-ui/index.html`, con candado verde.

El servicio de Caddy está detrás de un `profile` de Compose, así que **por defecto no arranca** y nada cambia si no lo activás. Encenderlo es una línea en un archivo; apagarlo, borrarla.

Dos detalles que están resueltos y conviene saber por qué: los certificados persisten en un volumen, porque Let's Encrypt limita a **cinco por dominio por semana** y sin persistencia cada redespliegue pediría uno nuevo hasta quedarse sin sitio durante días. Y el proxy pasa las cabeceras `X-Forwarded-*`, sin las cuales Spring creería que todo llega por HTTP plano y generaría enlaces `http://` que el navegador bloquearía por contenido mixto.

## 6.6 El primer despliegue

Con todo lo anterior hecho:

**Actions → CD - Despliegue en OCI → Run workflow** (dejá `image_tag` vacío).

Toma entre ocho y doce minutos. Después:

```bash
bash scripts/smoke-test.sh <IP_PUBLICA>
```

El script detecta solo que está en producción y verifica por la API pública.

## 6.7 Lo único que hay que coordinar con el equipo

No es trabajo tuyo, pero sí es un riesgo tuyo, así que corresponde dejarlo por escrito.

**`/actuator/health` tiene que quedar en `permitAll()`** cuando el equipo de backend implemente `SecurityConfig` y `AuthenticationFilter`. Si esa ruta empieza a devolver 401, el healthcheck de la imagen falla, el contenedor queda marcado como no sano aunque la API funcione perfectamente, y **el despliegue se revierte solo en cada intento**.

Está anotado en `application.properties`, que es donde lo va a leer quien escriba la seguridad. Aun así, decíselo por escrito **antes** de que lo escriban: descubrirlo después cuesta un despliegue fallido y una hora de confusión buscando en el lugar equivocado.

Todo lo demás que falte del backend o del modelo **no bloquea la infraestructura**. Podés desplegar hoy: la API queda arriba, con Swagger y el servicio de inferencia funcionando. El endpoint de negocio devolverá 404 hasta que lo implementen, y el smoke test lo reporta como pendiente, no como fallo — precisamente para que la infraestructura se pueda validar sin esperar a nadie.

---

## 7. Lo que queda fuera de alcance

Ser honesto acá vale más que inflar la lista, y un jurado lo valora:

- **Una sola instancia, sin alta disponibilidad.** Si la VM se cae, el servicio se cae. Resolverlo exige balanceador y más de una máquina, y eso ya no entra en la capa gratuita.
- **El estado de Terraform es local.** Funciona con una sola persona aplicando. Si el equipo creciera, habría que moverlo a un backend remoto para que dos personas no apliquen a la vez y se pisen.
- **El monitoreo avisa por issue, no por notificación push.** Detecta caídas, pero nadie se entera a los dos minutos si no está mirando GitHub. Un webhook a Discord o Slack sería el siguiente paso.
- **La cadencia del monitoreo es de 15 minutos.** Es el mínimo que GitHub respeta en workflows programados. Para medir disponibilidad con precisión haría falta una herramienta externa.
- **El backup pausa el backend un instante.** Es la única forma de obtener una copia consistente de H2 sin montar replicación. Con una base de verdad (PostgreSQL y `pg_dump`) no haría falta.
- **HTTPS está construido pero apagado.** El proxy con certificado automático está listo; se enciende con dos líneas en el `.env` de la VM. No se dejó activo por defecto porque exige tener ya la IP y abrir dos puertos más.

Ninguna de estas es un descuido: son decisiones de alcance, tomadas para llegar a un despliegue funcional y reproducible dentro del plazo.

---

## 8. Orden sugerido

| Cuándo | Qué |
|---|---|
| Ahora mismo | Generar la clave SSH y la API key de OCI (5 minutos, en la consola) |
| Ahora mismo | **`terraform apply`.** Crea todo, incluida la instancia A1 — que es el único riesgo que no controlás |
| Hoy | Renombrar el `.env` de la raíz, commit y push desde GitHub Desktop, PR a `develop` |
| Hoy | `provision-vm.sh` y completar el `.env` de la VM |
| Después | `./scripts/configurar-github.sh` con las salidas de Terraform |
| Después | Merge a `main` → primer despliegue |
| Si sobra tiempo | Caddy + sslip.io para HTTPS (`habilitar_https = true` en el tfvars) |

---

## 9. Cómo defenderlo ante el jurado

### Los 60 segundos

> "Mi rol fue infraestructura y entrega continua. La infraestructura de Oracle está en Terraform: red, instancia, bucket y permisos se crean con un comando. Los dos servicios se levantan en local con `make up`, y llegan a producción solos cada vez que se mergea a `main`: se entrena el modelo, se publica en Object Storage, se construyen imágenes ARM, se suben al registro de Oracle y se actualizan los contenedores por SSH. Si el healthcheck falla, revierte solo a la versión anterior. La VM no guarda ningún secreto de Oracle: se autentica con instance principal. Y el servicio de inferencia no está expuesto a internet — solo el backend lo alcanza, y hay una comprobación en CI que falla si alguien intenta publicarlo."

### Preguntas probables

**"¿Por qué no usaron una plataforma tipo Vercel o Netlify?"**
No podrían hostearlo: es una JVM permanente, un proceso Python con scikit-learn en memoria y una base con estado. Esas plataformas sirven para frontends estáticos y funciones efímeras.

**"¿Cómo vuelven atrás si un despliegue sale mal?"**
Automático. Las imágenes se etiquetan con el SHA del commit, nunca `latest`. Si el healthcheck posterior no pasa, el workflow reinstala la etiqueta anterior sin intervención. Para un rollback deliberado, se relanza el workflow indicando el SHA.

**"¿Dónde están las credenciales?"**
Siete secrets en GitHub para la canalización. En la VM, **cero** secretos de Oracle: usa instance principal con un Dynamic Group y una Policy de solo lectura sobre un bucket específico.

**"¿Esto funciona en la máquina de cualquiera del equipo?"**
Sí, y es el punto de haber contenerizado todo. macOS, Windows o Linux: solo se necesita Docker. No hay que instalar JDK, Maven ni Python, porque nada se compila fuera de un contenedor.

**"¿Cómo saben que el modelo no empeoró?"**
CI publica las métricas de cada entrenamiento como artefacto descargable en cada PR. Las pruebas verifican el contrato del servicio, no la calidad del modelo, para que reentrenar no rompa el pipeline.

**"¿Y si tienen que reconstruir todo desde cero?"**
`terraform apply`. La infraestructura está en código y versionada.

**"¿Qué falta?"**
HTTPS, monitoreo con alertas, backup automático y alta disponibilidad. Están identificados y priorizados en la sección 7; no se hicieron por plazo, no por olvido.

---

## 10. Referencias

- **Guía completa de despliegue y runbook:** [`docs/devops/despliegue-oci.md`](despliegue-oci.md)
- **Infraestructura como código:** [`infra/terraform/README.md`](../../infra/terraform/README.md)
- **Puesta en marcha local:** [`README.md`](../../README.md)
- **Comandos disponibles:** `make` (sin argumentos)
