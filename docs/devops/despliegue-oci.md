# Despliegue de TechMind en Oracle Cloud Infrastructure

Guía completa para llevar TechMind desde cero hasta una API funcionando en la
capa **Always Free** de OCI, con despliegue automático desde GitHub Actions.

Este documento es el que referencian [`docker-compose.yml`](../../docker-compose.yml),
[`.env.example`](../../.env.example), [`cd.yml`](../../.github/workflows/cd.yml)
y [`provision-vm.sh`](../../scripts/provision-vm.sh).

**Tiempo estimado la primera vez:** 90–120 minutos, casi todos en la consola web
de OCI. Los despliegues siguientes son automáticos y tardan 8–12 minutos.

---

## Índice

1. [Arquitectura desplegada](#1-arquitectura-desplegada)
2. [Antes de empezar](#2-antes-de-empezar)
3. [Parte 1 — Red y máquina virtual](#parte-1--red-y-máquina-virtual)
4. [Parte 2 — Object Storage](#parte-2--object-storage)
5. [Parte 3 — Identidad y permisos](#parte-3--identidad-y-permisos)
6. [Parte 4 — Aprovisionar la VM](#parte-4--aprovisionar-la-vm)
7. [Parte 5 — Secrets y variables en GitHub](#parte-5--secrets-y-variables-en-github)
8. [Parte 6 — Primer despliegue](#parte-6--primer-despliegue)
9. [Parte 7 — Operación diaria (runbook)](#parte-7--operación-diaria-runbook)
10. [Parte 8 — Diagnóstico de fallos](#parte-8--diagnóstico-de-fallos)
11. [Apéndice A — Si no hay capacidad Ampere A1](#apéndice-a--si-no-hay-capacidad-ampere-a1)
12. [Apéndice B — Inventario de secrets y variables](#apéndice-b--inventario-de-secrets-y-variables)

---

## 1. Arquitectura desplegada

```
   GitHub Actions (runner efímero)
     │
     ├─ entrena el modelo ──────────────► OCI Object Storage
     │                                      bucket: techmind-models
     │                                      objeto: model.joblib
     │
     ├─ construye imágenes (linux/arm64) ► OCIR
     │                                      techmind/backend:<sha>
     │                                      techmind/ml-service:<sha>
     │
     └─ ssh ────────────────────────────► OCI Compute (VM.Standard.A1.Flex)
                                            /opt/techmind/
                                              docker-compose.yml
                                              docker-compose.prod.yml
                                              .env
                                            │
                                            ├── contenedor backend  :8080 ─► Internet
                                            └── contenedor ml-service :8000 (solo red interna)
                                                  descarga model.joblib
                                                  con instance_principal
```

Puntos de diseño que conviene entender antes de tocar nada:

- **En la VM no se compila nada.** Se despliegan imágenes ya construidas y
  probadas en CI. La VM solo hace `pull` y `up`.
- **El ml-service no publica puerto.** Solo el backend es accesible desde fuera.
- **La VM no tiene credenciales de OCI en disco.** Usa `instance_principal`:
  se autentica con su propia identidad mediante un Dynamic Group y una Policy.
- **La etiqueta de imagen es el SHA del commit**, nunca `latest`. Volver atrás
  es cambiar una variable.

---

## 2. Antes de empezar

Necesitás:

- Una cuenta de OCI con la capa Always Free activa.
- Permisos de administrador en el repositorio de GitHub (para crear secrets).
- Un par de claves SSH dedicado al despliegue. **No reutilices tu clave
  personal**: esta clave va a vivir dentro de un secret de GitHub.

```bash
ssh-keygen -t ed25519 -C "techmind-deploy" -f ~/.ssh/techmind_deploy -N ""
```

Eso genera dos archivos:

| Archivo | Dónde va |
|---|---|
| `~/.ssh/techmind_deploy.pub` | se pega al crear la instancia en OCI |
| `~/.ssh/techmind_deploy` | se guarda como secret `OCI_SSH_PRIVATE_KEY` |

Opcional pero muy recomendable, la CLI de OCI en tu máquina para verificar
valores sin abrir la consola web:

```bash
bash -c "$(curl -L https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)"
oci setup config      # te va a pedir user OCID, tenancy OCID y región
```

---

## Parte 1 — Red y máquina virtual

### 1.1 Anotá tu compartment

Consola → **Identity & Security → Compartments**. Podés usar el compartment raíz
(el que lleva el nombre de tu tenancy) para simplificar. Copiá su OCID:

```
ocid1.compartment.oc1..aaaa....
```

Lo vas a necesitar en la Policy de la Parte 3.

### 1.2 Crear la VCN

Consola → **Networking → Virtual Cloud Networks → Start VCN Wizard** →
**Create VCN with Internet Connectivity**.

| Campo | Valor |
|---|---|
| VCN Name | `techmind-vcn` |
| VCN CIDR Block | `10.0.0.0/16` |
| Public Subnet CIDR | `10.0.0.0/24` |
| Private Subnet CIDR | `10.0.1.0/24` |

El asistente crea de una sola vez la VCN, la subred pública, el Internet Gateway
y la tabla de rutas. Hacerlo a mano es donde más gente se equivoca.

### 1.3 Abrir el puerto 8080 en la Security List

Este paso es **obligatorio** y es la causa número uno de "el contenedor corre
pero no puedo entrar desde fuera".

Consola → tu VCN → **Security Lists** → `Default Security List for techmind-vcn`
→ **Add Ingress Rules**:

| Campo | Valor |
|---|---|
| Stateless | No |
| Source Type | CIDR |
| Source CIDR | `0.0.0.0/0` |
| IP Protocol | TCP |
| Destination Port Range | `8080` |
| Description | `API publica TechMind` |

El puerto 22 ya viene abierto por el asistente. Si querés endurecerlo, cambiá su
`Source CIDR` a tu IP (`curl ifconfig.me` te la dice) **más** los rangos de los
runners de GitHub — pero ojo, esos rangos cambian y son cientos. Para un
hackathon, dejalo en `0.0.0.0/0` y protegé con la clave SSH.

> **Recordá:** OCI tiene **dos** capas de firewall. Esta es la de la red. La del
> sistema operativo la abre `provision-vm.sh` en la Parte 4. Hay que abrir las
> dos.

### 1.4 Crear la instancia

Consola → **Compute → Instances → Create Instance**.

| Campo | Valor |
|---|---|
| Name | `techmind-vm` |
| Image | **Oracle Linux 9** (es la que asume `provision-vm.sh`) |
| Shape | **VM.Standard.A1.Flex** — 2 OCPU, 12 GB RAM |
| VCN / Subnet | `techmind-vcn` / la subred **pública** |
| Assign public IPv4 address | **Sí** |
| SSH keys | pegar el contenido de `~/.ssh/techmind_deploy.pub` |

> **Aviso importante sobre A1.** El shape Ampere A1 sufre `Out of host capacity`
> de forma crónica en muchas regiones. Si te lo rechaza, no esperes: probá otro
> Availability Domain, probá a otra hora, o pasá al
> [Apéndice A](#apéndice-a--si-no-hay-capacidad-ampere-a1). **Creá la instancia
> el primer día del proyecto**, no la semana de la entrega.

Anotá la **IP pública** de la instancia: es el valor de la variable `OCI_HOST`.

Verificá el acceso antes de seguir:

```bash
ssh -i ~/.ssh/techmind_deploy opc@<IP_PUBLICA>
```

El usuario por defecto en Oracle Linux es `opc` (en Ubuntu sería `ubuntu`).

---

## Parte 2 — Object Storage

### 2.1 Obtener el namespace

El namespace del tenancy es un identificador único que aparece tanto en la ruta
de OCIR como en las llamadas a Object Storage.

```bash
oci os ns get
# {"data": "axxxxxxxxxxx"}
```

Sin la CLI: consola → menú de perfil (arriba a la derecha) → **Tenancy** →
campo **Object Storage Namespace**.

Ese valor es la variable `OCI_NAMESPACE`.

### 2.2 Crear el bucket

Consola → **Storage → Buckets → Create Bucket**.

| Campo | Valor |
|---|---|
| Bucket Name | `techmind-models` |
| Default Storage Tier | Standard |
| Visibility | **Private** |

El nombre tiene que coincidir con la variable `OCI_BUCKET_NAME`. Dejalo privado:
el acceso se resuelve con identidad (Parte 3), no exponiendo el bucket.

---

## Parte 3 — Identidad y permisos

Acá se configuran **tres identidades distintas** con tres propósitos distintos.
Confundirlas es el error más común de esta guía.

| Identidad | Quién la usa | Para qué |
|---|---|---|
| **Instance Principal** | la VM | descargar `model.joblib` del bucket |
| **Usuario IAM + API key** | el job `publicar-modelo` de GitHub | subir el modelo al bucket |
| **Auth Token** | el job `publicar-imagenes` y la VM | login en OCIR |

### 3.1 Instance Principal — para que la VM lea el bucket sin secretos

Este es el mecanismo que permite que [`docker-compose.prod.yml`](../../docker-compose.prod.yml)
use `OCI_AUTH_METHOD: instance_principal` y que en la VM **no haya ninguna clave
privada en disco**.

**Paso 1 — Dynamic Group.** Consola → **Identity & Security → Domains → Default
domain → Dynamic Groups → Create Dynamic Group**.

| Campo | Valor |
|---|---|
| Name | `techmind-instances` |
| Rule | `ALL {instance.compartment.id = 'ocid1.compartment.oc1..aaaa....'}` |

Reemplazá el OCID por el de tu compartment (Parte 1.1). Si preferís acotarlo a
una sola máquina, usá `instance.id = 'ocid1.instance.oc1....'`.

**Paso 2 — Policy.** Consola → **Identity & Security → Policies → Create Policy**
→ pestaña **Show manual editor**.

| Campo | Valor |
|---|---|
| Name | `techmind-instance-storage` |
| Compartment | el mismo del Dynamic Group |

```
Allow dynamic-group techmind-instances to read objects in compartment id ocid1.compartment.oc1..aaaa.... where target.bucket.name = 'techmind-models'
```

Si tu tenancy usa Identity Domains, el nombre del grupo lleva el prefijo del
dominio:

```
Allow dynamic-group 'Default'/'techmind-instances' to read objects in compartment ...
```

Solo `read`. La VM nunca debe poder escribir en el bucket: quien publica modelos
es la canalización, no el servidor.

### 3.2 Usuario IAM + API key — para que CI suba el modelo

Consola → **Identity & Security → Domains → Default domain → Users** → creá o
elegí un usuario (podés usar el tuyo) → **API Keys → Add API Key → Generate API
Key Pair**.

Descargá la clave privada y guardá el bloque de configuración que muestra OCI:

```ini
[DEFAULT]
user=ocid1.user.oc1..aaaa....           <- secret OCI_CLI_USER
fingerprint=a1:b2:c3:...                <- secret OCI_CLI_FINGERPRINT
tenancy=ocid1.tenancy.oc1..aaaa....     <- secret OCI_CLI_TENANCY
region=sa-saopaulo-1                    <- variable OCI_REGION
```

El contenido completo del `.pem` descargado (incluidas las líneas
`-----BEGIN PRIVATE KEY-----` y `-----END PRIVATE KEY-----`) es el secret
`OCI_CLI_KEY_CONTENT`.

Ese usuario necesita poder escribir en el bucket y en OCIR. Si es administrador
del tenancy ya los tiene. Si no, agregá una policy:

```
Allow group <tu-grupo> to manage objects in compartment id ocid1.compartment.oc1..aaaa.... where target.bucket.name = 'techmind-models'
Allow group <tu-grupo> to manage repos in tenancy
```

### 3.3 Auth Token — para OCIR

**No es la contraseña de la consola.** Es un token específico de OCIR.

Consola → mismo usuario → **Auth Tokens → Generate Token** → descripción
`techmind-ocir`. **Se muestra una sola vez.** Ese valor es el secret
`OCIR_AUTH_TOKEN`.

El nombre de usuario de OCIR (`OCIR_USERNAME`) tiene una de estas dos formas:

```
<namespace>/<usuario>                    # tenancy clásico
<namespace>/<dominio>/<usuario>          # tenancy con Identity Domains
```

Ejemplos:

```
axxxxxxxxxxx/alexanderviveros9@gmail.com
axxxxxxxxxxx/Default/alexanderviveros9@gmail.com
```

Verificalo antes de confiar en él:

```bash
docker login gru.ocir.io -u '<namespace>/<usuario>'
# password: el Auth Token
```

Si falla, es casi siempre porque falta el prefijo del dominio.

### 3.4 Registro OCIR según tu región

La variable `OCIR_REGISTRY` depende de la región de tu tenancy:

| Región | `OCIR_REGISTRY` |
|---|---|
| São Paulo (`sa-saopaulo-1`) | `gru.ocir.io` |
| Santiago (`sa-santiago-1`) | `scl.ocir.io` |
| Bogotá (`sa-bogota-1`) | `bog.ocir.io` |
| Ashburn (`us-ashburn-1`) | `iad.ocir.io` |
| Phoenix (`us-phoenix-1`) | `phx.ocir.io` |

---

## Parte 4 — Aprovisionar la VM

[`provision-vm.sh`](../../scripts/provision-vm.sh) instala Docker, verifica la
versión de Compose, abre el puerto 8080 en el firewall del sistema operativo,
crea `/opt/techmind` y configura la rotación de logs. Es idempotente: podés
relanzarlo sin miedo.

```bash
scp -i ~/.ssh/techmind_deploy scripts/provision-vm.sh opc@<IP>:~
ssh -i ~/.ssh/techmind_deploy opc@<IP> 'bash provision-vm.sh'
```

El script **falla a propósito** si la versión de Docker Compose es anterior a la
2.24: [`docker-compose.prod.yml`](../../docker-compose.prod.yml) usa etiquetas
`!reset`, que no existen antes de esa versión y provocan un error de parseo del
YAML. Si te falla ahí:

```bash
sudo dnf update -y docker-compose-plugin
```

Cerrá y reabrí la sesión SSH al terminar, para que el usuario tome el grupo
`docker`.

### 4.1 Completar el `.env` de producción

El script deja una plantilla en `/opt/techmind/.env` con permisos `600`.
**Completala a mano.** La canalización de despliegue solo toca su línea
`IMAGE_TAG`; el resto de valores viven únicamente en la VM.

```bash
ssh -i ~/.ssh/techmind_deploy opc@<IP>
vi /opt/techmind/.env
```

```ini
# --- OCIR / Object Storage ---
OCIR_REGISTRY=gru.ocir.io
OCI_NAMESPACE=axxxxxxxxxxx
OCI_REGION=sa-saopaulo-1
OCI_BUCKET_NAME=techmind-models
OCI_MODEL_OBJECT=model.joblib

# --- Versión desplegada (la actualiza el workflow de CD) ---
IMAGE_TAG=latest

# --- Backend ---
BACKEND_PORT=8080
DB_USERNAME=techmind
DB_PASSWORD=<generala con: openssl rand -base64 24>
TECHMIND_API_KEY=<generala con: openssl rand -hex 32>
CORS_ALLOWED_ORIGINS=http://<IP_PUBLICA>:8080
INFERENCE_SERVICE_TIMEOUT_MS=8000
```

> **Trampa de H2 que te va a costar una hora si no la conocés.** La base H2 se
> crea en el primer arranque con el usuario y contraseña que haya en ese momento.
> Si después cambiás `DB_PASSWORD`, el backend falla con
> `Wrong user name or password [28000-...]` y el único arreglo es borrar el
> volumen (`docker volume rm techmind_backend-data`), perdiendo los datos.
> **Elegí la contraseña una vez y no la toques.**

`DB_USERNAME`, `DB_PASSWORD` y `TECHMIND_API_KEY` son obligatorias: en
[`docker-compose.prod.yml`](../../docker-compose.prod.yml) están declaradas con
la sintaxis `${VAR:?mensaje}`, de modo que si faltan el despliegue falla de
inmediato y con un mensaje claro, en lugar de arrancar mal.

---

## Parte 5 — Secrets y variables en GitHub

Repositorio → **Settings → Secrets and variables → Actions**.

### 5.1 Secrets (pestaña *Secrets*)

| Secret | Valor | De dónde sale |
|---|---|---|
| `OCI_CLI_USER` | `ocid1.user.oc1..` | Parte 3.2 |
| `OCI_CLI_TENANCY` | `ocid1.tenancy.oc1..` | Parte 3.2 |
| `OCI_CLI_FINGERPRINT` | `a1:b2:c3:...` | Parte 3.2 |
| `OCI_CLI_KEY_CONTENT` | contenido completo del `.pem` | Parte 3.2 |
| `OCIR_USERNAME` | `<namespace>/<usuario>` | Parte 3.3 |
| `OCIR_AUTH_TOKEN` | el Auth Token | Parte 3.3 |
| `OCI_SSH_PRIVATE_KEY` | contenido de `~/.ssh/techmind_deploy` | Parte 2 del preámbulo |

Las dos claves privadas se pegan **enteras**, incluyendo las líneas
`-----BEGIN ...-----` y `-----END ...-----` y el salto de línea final.

### 5.2 Variables (pestaña *Variables*)

| Variable | Ejemplo |
|---|---|
| `OCI_REGION` | `sa-saopaulo-1` |
| `OCI_NAMESPACE` | `axxxxxxxxxxx` |
| `OCI_BUCKET_NAME` | `techmind-models` |
| `OCIR_REGISTRY` | `gru.ocir.io` |
| `OCI_HOST` | IP pública de la instancia |
| `OCI_SSH_USER` | `opc` |
| `TARGET_PLATFORM` | `linux/arm64` (opcional; ver Apéndice A) |

### 5.3 Crear el environment `produccion`

Repositorio → **Settings → Environments → New environment** → nombre
`produccion`.

[`cd.yml`](../../.github/workflows/cd.yml) lo referencia pero **no lo crea**. Si
no existe, el job `desplegar` falla. Además, dentro del environment podés activar
**Required reviewers** para exigir una aprobación manual antes de tocar
producción — recomendable si más de una persona hace merge a `main`.

---

## Parte 6 — Primer despliegue

### 6.1 Lanzarlo

Se dispara solo al hacer merge a `main`. Para lanzarlo a mano:

Repositorio → **Actions → CD - Despliegue en OCI → Run workflow** → dejá
`image_tag` vacío para desplegar el commit actual.

### 6.2 Qué hace, en orden

| Job | Qué hace | Si falla |
|---|---|---|
| `verificar` | pruebas de backend y ml-service | no se despliega nada |
| `publicar-modelo` | entrena y sube `model.joblib` al bucket | revisá las credenciales de la Parte 3.2 |
| `publicar-imagenes` | construye para ARM y publica en OCIR | revisá `OCIR_USERNAME` (Parte 3.3) |
| `desplegar` | `scp` + `ssh` a la VM, `pull` y `up --wait` | **revierte solo** a la etiqueta anterior |

El paso final verifica la API **desde el runner**, no por SSH: eso confirma que
pasa por la Security List y el firewall, no solo que funciona dentro de la
máquina.

### 6.3 Comprobar el resultado

```bash
# desde tu máquina
curl http://<IP_PUBLICA>:8080/actuator/health
bash scripts/smoke-test.sh <IP_PUBLICA>
```

`smoke-test.sh` detecta que el puerto 8000 no está publicado —es lo correcto en
producción— y verifica únicamente a través de la API pública.

Swagger queda en `http://<IP_PUBLICA>:8080/swagger-ui/index.html`.

---

## Parte 7 — Operación diaria (runbook)

Todos estos comandos se ejecutan **dentro de la VM**, en `/opt/techmind`.

```bash
ssh -i ~/.ssh/techmind_deploy opc@<IP>
cd /opt/techmind
```

Desde el repositorio hay atajos en el [`Makefile`](../../Makefile): `make prod-up`,
`make prod-down`, `make prod-ps`, `make prod-logs`.

### Estado y logs

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f --tail=100
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs backend --tail=200
```

### Reiniciar

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart backend
```

### Rollback deliberado

El automático ya ocurre si los healthchecks fallan. Para volver atrás a mano:

**Opción A (recomendada, deja rastro en Actions):** Actions → *CD - Despliegue en
OCI* → **Run workflow** → poné en `image_tag` el SHA de la versión buena.

**Opción B (urgencia, desde la VM):**

```bash
cd /opt/techmind
sed -i 's|^IMAGE_TAG=.*|IMAGE_TAG=<sha-anterior>|' .env
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --wait
```

Los SHA disponibles son los de los commits de `main` cuyo CD terminó en verde.

### Volver a un modelo anterior

Cada corrida sube el modelo dos veces: como `model.joblib` (el que se sirve) y
como `modelos/model-<sha>.joblib` (histórico inmutable). Para restaurar uno:

```bash
oci os object copy \
  --bucket-name techmind-models \
  --source-object-name "modelos/model-<sha>.joblib" \
  --destination-object-name "model.joblib" \
  --destination-namespace <namespace> \
  --destination-bucket techmind-models \
  --destination-region <region>

# y reiniciar el servicio para que lo vuelva a descargar
ssh opc@<IP> 'cd /opt/techmind && docker compose -f docker-compose.yml -f docker-compose.prod.yml restart ml-service'
```

Las métricas del modelo de cada versión quedan en `modelos/metrics-<sha>.json`.

### Backup de la base de datos

La base H2 vive en el volumen `techmind_backend-data`. No hay backup automático;
si el proyecto pasa de hackathon a algo real, esto es lo primero que hay que
agregar. Copia manual:

```bash
docker run --rm \
  -v techmind_backend-data:/data:ro \
  -v /tmp:/backup \
  alpine tar czf /backup/techmind-$(date +%F).tar.gz -C /data .

# y bajarlo a tu máquina
scp -i ~/.ssh/techmind_deploy opc@<IP>:/tmp/techmind-*.tar.gz .
```

### Espacio en disco

```bash
df -h /
docker system df
docker image prune -af --filter "until=168h"
```

El CD ya hace `image prune` en cada despliegue, y tanto
[`docker-compose.prod.yml`](../../docker-compose.prod.yml) como
`/etc/docker/daemon.json` limitan los logs a 10 MB × 3 por contenedor. Aun así,
revisalo si la VM lleva semanas corriendo.

---

## Parte 8 — Diagnóstico de fallos

| Síntoma | Causa más probable | Qué hacer |
|---|---|---|
| `curl` a la IP se queda colgado | falta la regla de ingress en la Security List | Parte 1.3 |
| `curl` da *connection refused* | firewall del SO cerrado | volver a correr `provision-vm.sh` |
| `exec format error` al arrancar el contenedor | imagen amd64 en una VM ARM | fijar `TARGET_PLATFORM` correctamente |
| `no match for platform in manifest` al construir | la imagen base no existe para arm64 | ver la nota sobre imágenes base, abajo |
| `permission denied on /var/run/docker.sock` | el usuario no está en el grupo `docker` | `sudo usermod -aG docker opc` y reabrir la sesión SSH |
| `unknown tag !reset` | Docker Compose < 2.24 | `sudo dnf update docker-compose-plugin` |
| `unauthorized` al hacer pull de OCIR | `OCIR_USERNAME` sin el prefijo del dominio | Parte 3.3 |
| ml-service *unhealthy*, `/health` dice `NotAuthenticated` | falta el Dynamic Group o la Policy | Parte 3.1 |
| ml-service *unhealthy*, `/health` dice `ObjectNotFound` | el bucket está vacío | correr el job `publicar-modelo` |
| backend *unhealthy* pero `docker logs` se ve bien | el healthcheck no llega a `/actuator/health` | ver la nota de abajo |
| `Wrong user name or password [28000]` | cambió `DB_PASSWORD` después del primer arranque | ver la trampa de H2 en la Parte 4.1 |
| `Schema-validation: missing table` | `ddl-auto=validate` contra una base vacía | ver `application-prod.properties` |
| El despliegue revierte solo, sin causa clara | los healthchecks no pasaron en 180 s | `docker compose ... logs --tail=200` en la VM |

### Nota sobre las imágenes base y la arquitectura

La VM Always Free es **ARM** (Ampere A1), así que **toda imagen base tiene que
publicar una variante `linux/arm64`**. No todas lo hacen, y las que no lo hacen
fallan durante el build, no en el arranque:

```
FROM eclipse-temurin:17-jre-alpine
ERROR: no match for platform in manifest: not found
```

Eclipse Temurin publica sus imágenes **Alpine solo para amd64**. Por eso el
backend usa `eclipse-temurin:17-jre-jammy`, que sí es multiarquitectura. Antes
de cambiar cualquier `FROM`, verificalo:

```bash
docker manifest inspect <imagen> | grep '"architecture"' | sort -u
```

El job `docker` de [`ci.yml`](../../.github/workflows/ci.yml) comprueba esto
automáticamente en cada PR, contra la plataforma de `TARGET_PLATFORM`. Es una
comprobación que hace falta porque CI construye para amd64 (la arquitectura del
runner) y sin ella un `FROM` incompatible pasaría en verde hasta llegar al
despliegue.

### Nota sobre el healthcheck del backend

[`backend/Dockerfile`](../../backend/Dockerfile) chequea `/actuator/health`. Esa
ruta la expone `spring-boot-starter-actuator` y está configurada en
[`application.properties`](../../backend/src/main/resources/application.properties).

**Si el equipo de backend implementa `SecurityConfig` y `AuthenticationFilter`,
tiene que dejar `/actuator/health` como `permitAll()`.** Si esa ruta empieza a
devolver 401, el contenedor queda marcado como *unhealthy* aunque funcione
perfectamente, `docker compose up --wait` da por fallado el despliegue y **el CD
revierte en cada intento**. Es un acuerdo entre DevOps y backend, no una opción.

Comprobación rápida dentro de la VM:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec backend \
  wget -q -O - http://localhost:8080/actuator/health
# esperado: {"status":"UP"}
```

---

## Apéndice A — Si no hay capacidad Ampere A1

Si OCI rechaza la creación de la instancia con `Out of host capacity`, la
alternativa Always Free es **`VM.Standard.E2.1.Micro`** (x86, 1 OCPU, **1 GB de
RAM**). Requiere **tres** cambios, y el tercero es obligatorio:

**1. Arquitectura de las imágenes.** Variable de repositorio:

```
TARGET_PLATFORM = linux/amd64
```

**2. Bajar los límites de memoria.** Los actuales (768 MB + 768 MB = 1.5 GB) no
entran en 1 GB. En [`docker-compose.prod.yml`](../../docker-compose.prod.yml):

```yaml
ml-service:
  deploy:
    resources:
      limits:
        memory: 450M
backend:
  deploy:
    resources:
      limits:
        memory: 450M
```

**3. Crear swap.** Con 1 GB reales, scikit-learn y la JVM juntos van a rozar el
límite y el kernel va a matar procesos (OOM). En la VM:

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Aun así, esta configuración es apretada. Si la demo es importante, insistí con
A1 en otro Availability Domain antes de conformarte con el micro.

---

## Apéndice B — Inventario de secrets y variables

Referencia rápida de dónde vive cada valor. Ninguno de estos se versiona.

| Nombre | GitHub Secret | GitHub Variable | `/opt/techmind/.env` |
|---|:---:|:---:|:---:|
| `OCI_CLI_USER` | ✔ | | |
| `OCI_CLI_TENANCY` | ✔ | | |
| `OCI_CLI_FINGERPRINT` | ✔ | | |
| `OCI_CLI_KEY_CONTENT` | ✔ | | |
| `OCIR_USERNAME` | ✔ | | |
| `OCIR_AUTH_TOKEN` | ✔ | | |
| `OCI_SSH_PRIVATE_KEY` | ✔ | | |
| `OCI_REGION` | | ✔ | ✔ |
| `OCI_NAMESPACE` | | ✔ | ✔ |
| `OCI_BUCKET_NAME` | | ✔ | ✔ |
| `OCIR_REGISTRY` | | ✔ | ✔ |
| `OCI_HOST` | | ✔ | |
| `OCI_SSH_USER` | | ✔ | |
| `TARGET_PLATFORM` | | ✔ | |
| `OCI_MODEL_OBJECT` | | | ✔ |
| `IMAGE_TAG` | | | ✔ (la escribe el CD) |
| `BACKEND_PORT` | | | ✔ |
| `DB_USERNAME` | | | ✔ |
| `DB_PASSWORD` | | | ✔ |
| `TECHMIND_API_KEY` | | | ✔ |
| `CORS_ALLOWED_ORIGINS` | | | ✔ |
| `INFERENCE_SERVICE_TIMEOUT_MS` | | | ✔ |

### Qué NO está resuelto

Honestidad sobre el alcance actual, para que nadie asuma garantías que no
existen:

- **No hay HTTPS.** La API se sirve en HTTP plano por el 8080. Para un entorno
  real hace falta un proxy inverso (Caddy o Nginx) con certificado.
- **No hay backup automático** de la base H2. Solo el procedimiento manual de la
  Parte 7.
- **No hay monitoreo ni alertas.** Si el servicio se cae a las 3 AM, nadie se
  entera hasta que alguien lo prueba.
- **La infraestructura de OCI no está en código.** Todo lo de las Partes 1–3 es
  manual en la consola. Reconstruirla desde cero exige repetir esta guía.
