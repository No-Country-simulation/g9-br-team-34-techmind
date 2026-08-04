# Runbook — Despliegue en Oracle Cloud Infrastructure

Guía paso a paso, desde una cuenta de OCI recién creada hasta la API respondiendo
en Internet.

**Tiempo estimado:** 60–90 minutos la primera vez. Los despliegues siguientes son
automáticos: basta un merge a `main`.

> Todo lo que aparece entre `<ángulos>` hay que sustituirlo por un valor real.
> Conviene ir anotando los valores a medida que se obtienen: al final se
> necesitan casi todos para configurar GitHub.

---

## Índice

1. [Crear la cuenta y el compartment](#1-cuenta-y-compartment)
2. [Object Storage: bucket para los modelos](#2-object-storage)
3. [Compute: la máquina virtual](#3-compute)
4. [Red: abrir el puerto 8080](#4-red)
5. [Aprovisionar la VM](#5-aprovisionar-la-vm)
6. [Instance principal: acceso sin credenciales](#6-instance-principal)
7. [OCIR: registro de imágenes](#7-ocir)
8. [Claves de API para GitHub Actions](#8-claves-de-api)
9. [Configurar GitHub](#9-configurar-github)
10. [Primer despliegue](#10-primer-despliegue)
11. [Operación diaria](#11-operación-diaria)
12. [Diagnóstico de problemas](#12-diagnóstico-de-problemas)

---

## 1. Cuenta y compartment

### 1.1 Crear la cuenta

En <https://www.oracle.com/cloud/free/>. Pide tarjeta de crédito para verificar
identidad, pero **los recursos Always Free no se cobran**. Todo lo que usa este
proyecto entra en esa capa.

Al registrarse hay que elegir una **home region**. Es permanente. Para
Latinoamérica conviene São Paulo (`sa-saopaulo-1`) o Santiago (`sa-santiago-1`)
por latencia.

### 1.2 Crear un compartment

Un compartment agrupa recursos. Trabajar en el compartment raíz funciona, pero
mezcla todo lo del proyecto con lo demás del tenancy.

**Consola → Identity & Security → Compartments → Create Compartment**

- Name: `techmind`
- Description: `Hackathon ONE G9 - Team 34`
- Parent: el compartment raíz

Al crearlo, copiar su **OCID**.

> 📝 **Anotar:** `COMPARTMENT_OCID = ocid1.compartment.oc1..<...>`

### 1.3 Obtener el namespace del tenancy

Identifica al tenancy en Object Storage y en OCIR.

**Consola → Perfil (arriba a la derecha) → Tenancy** → campo *Object storage
namespace*.

> 📝 **Anotar:** `OCI_NAMESPACE = <namespace>` (algo como `grxxxxxxxxxx`)

---

## 2. Object Storage

Aquí viven los artefactos del modelo. Es la integración con OCI que exige el
brief del hackathon.

**Consola → Storage → Buckets → Create Bucket**

| Campo | Valor |
|---|---|
| Bucket Name | `techmind-models` |
| Compartment | `techmind` |
| Default Storage Tier | Standard |
| Encryption | Encrypt using Oracle managed keys |

Dejar **Visibility: Private**. El bucket no debe ser público: se accede mediante
instance principal desde la VM y con clave de API desde GitHub Actions.

> 📝 **Anotar:** `OCI_BUCKET_NAME = techmind-models`

---

## 3. Compute

**Consola → Compute → Instances → Create Instance**

| Campo | Valor |
|---|---|
| Name | `techmind-vm` |
| Compartment | `techmind` |
| Image | Oracle Linux 9 |
| Shape | `VM.Standard.A1.Flex` — **4 OCPU, 24 GB RAM** |

### Sobre el shape

`VM.Standard.A1.Flex` es ARM (Ampere). La capa Always Free da 4 OCPU y 24 GB de
RAM, repartibles entre hasta 4 instancias. Aquí se usa todo en una sola.

**Esto tiene una consecuencia directa en el CD:** las imágenes Docker deben
construirse para `linux/arm64`. Si se construyen para `amd64`, el contenedor
falla al arrancar con `exec format error`. El workflow ya usa `linux/arm64` por
defecto.

> Si `A1.Flex` da *"Out of host capacity"* — pasa a menudo en las regiones
> populares —, hay dos salidas: reintentar más tarde (la capacidad se libera),
> o usar `VM.Standard.E2.1.Micro`, que es x86 con 1 GB de RAM. Con esa opción
> **hay que crear la variable de repositorio `TARGET_PLATFORM = linux/amd64`**
> en GitHub. 1 GB de RAM es muy justo para la JVM más scikit-learn; habría que
> bajar los límites de memoria de `docker-compose.prod.yml`.

### Claves SSH

En *Add SSH keys*, generar un par nuevo **exclusivo para el despliegue**:

```bash
ssh-keygen -t ed25519 -C "techmind-deploy" -f ~/.ssh/techmind_deploy
```

Pegar el contenido de `~/.ssh/techmind_deploy.pub` en la consola.

La clave **privada** (`~/.ssh/techmind_deploy`, sin `.pub`) se cargará después
como secret de GitHub. No reutilizar la clave SSH personal: si hay que revocar
el acceso del pipeline, se debe poder hacer sin perder el acceso propio.

Crear la instancia y anotar su **IP pública**.

> 📝 **Anotar:** `OCI_HOST = <IP pública>` · `OCI_SSH_USER = opc`

---

## 4. Red

OCI tiene **dos capas de firewall** y hay que abrir las dos. Olvidar la segunda
es la causa número uno de "el contenedor corre pero no puedo entrar desde
fuera".

### 4.1 Security List de la VCN

**Consola → Networking → Virtual Cloud Networks →** la VCN de la instancia
**→ Subnets →** la subred pública **→ Security Lists →** la lista por defecto
**→ Add Ingress Rules**

| Campo | Valor |
|---|---|
| Stateless | No |
| Source Type | CIDR |
| Source CIDR | `0.0.0.0/0` |
| IP Protocol | TCP |
| Destination Port Range | `8080` |
| Description | `API REST TechMind` |

`0.0.0.0/0` deja la API abierta a Internet, que es lo que se quiere para la demo.
El puerto 22 ya está abierto por defecto.

### 4.2 Firewall del sistema operativo

Lo hace `scripts/provision-vm.sh` en el paso siguiente.

---

## 5. Aprovisionar la VM

```bash
# Desde la raíz del repositorio, en tu máquina
scp -i ~/.ssh/techmind_deploy scripts/provision-vm.sh opc@<IP>:~
ssh -i ~/.ssh/techmind_deploy opc@<IP> 'bash provision-vm.sh'
```

El script instala Docker Engine y Compose v2, agrega `opc` al grupo `docker`,
abre el 8080 en firewalld, crea `/opt/techmind` con una plantilla de `.env` con
permisos 600 y configura la rotación global de logs. Es idempotente: se puede
volver a ejecutar sin romper nada.

### 5.1 Completar el `.env` de la VM

```bash
ssh -i ~/.ssh/techmind_deploy opc@<IP>
nano /opt/techmind/.env
```

```bash
OCIR_REGISTRY=gru.ocir.io          # según región, ver tabla en el paso 7
OCI_NAMESPACE=<namespace del paso 1.3>
OCI_REGION=sa-saopaulo-1
OCI_BUCKET_NAME=techmind-models
OCI_MODEL_OBJECT=model.joblib

IMAGE_TAG=latest                    # lo actualiza el CD en cada despliegue

BACKEND_PORT=8080
DB_USERNAME=techmind
DB_PASSWORD=<generar una contraseña fuerte>
TECHMIND_API_KEY=<generar una clave fuerte>
CORS_ALLOWED_ORIGINS=http://<IP>:8080
```

Para generar valores aleatorios: `openssl rand -base64 32`

> El perfil `prod` de Spring declara `${DB_USERNAME}` y `${DB_PASSWORD}` **sin
> valor por defecto**. Si faltan, la aplicación no arranca. Es deliberado: es
> preferible un fallo inmediato y evidente a un servicio corriendo con
> credenciales vacías.

Cerrar la sesión SSH y volver a entrar, para que el grupo `docker` surta efecto.

---

## 6. Instance principal

Permite que la VM lea de Object Storage **sin ninguna clave privada guardada en
el servidor**. Se autentica con su propia identidad.

### 6.1 Dynamic group

**Consola → Identity & Security → Domains →** dominio por defecto
**→ Dynamic Groups → Create Dynamic Group**

- Name: `techmind-instances`
- Regla:

```
instance.compartment.id = '<COMPARTMENT_OCID del paso 1.2>'
```

Cualquier instancia de ese compartment queda incluida.

### 6.2 Policy

**Consola → Identity & Security → Policies → Create Policy** (en el compartment
raíz)

- Name: `techmind-instance-policy`
- Statements:

```
Allow dynamic-group techmind-instances to read objects in compartment techmind where target.bucket.name = 'techmind-models'
Allow dynamic-group techmind-instances to read repos in compartment techmind
```

`read objects` y no `manage objects`: la VM solo necesita descargar el modelo.
Quien lo sube es GitHub Actions, con otra identidad. Dar permisos de escritura
que nadie usa es superficie de ataque gratuita.

> Si el tenancy usa *identity domains*, el nombre del dynamic group puede
> necesitar el prefijo del dominio: `Allow dynamic-group 'Default'/'techmind-instances' to ...`

---

## 7. OCIR

El registro de contenedores de OCI. Aquí publica las imágenes GitHub Actions y
desde aquí las descarga la VM.

### 7.1 Endpoint según la región

| Región | Endpoint |
|---|---|
| São Paulo (`sa-saopaulo-1`) | `gru.ocir.io` |
| Santiago (`sa-santiago-1`) | `scl.ocir.io` |
| Bogotá (`sa-bogota-1`) | `bog.ocir.io` |
| Ashburn (`us-ashburn-1`) | `iad.ocir.io` |
| Phoenix (`us-phoenix-1`) | `phx.ocir.io` |

> 📝 **Anotar:** `OCIR_REGISTRY = <endpoint>`

### 7.2 Auth Token

**No es la contraseña de la consola.** Es un token específico.

**Consola → Perfil → My profile → Auth Tokens → Generate Token**

- Description: `github-actions-techmind`

**Se muestra una sola vez.** Copiarlo inmediatamente.

> 📝 **Anotar:** `OCIR_AUTH_TOKEN = <token>`

### 7.3 Usuario de OCIR

El formato depende del tenancy:

- Sin identity domains: `<namespace>/<usuario>`
  → `grxxxxxxxxxx/alexanderviveros9@gmail.com`
- Con identity domains: `<namespace>/<dominio>/<usuario>`
  → `grxxxxxxxxxx/Default/alexanderviveros9@gmail.com`

Para saber cuál: en **Identity → Domains**, si aparece un dominio llamado
`Default`, se usa la segunda forma.

> 📝 **Anotar:** `OCIR_USERNAME = <namespace>/[dominio/]<usuario>`

Verificar antes de seguir:

```bash
echo '<AUTH_TOKEN>' | docker login <OCIR_REGISTRY> --username '<OCIR_USERNAME>' --password-stdin
```

Si esto falla, el CD también fallará. Mejor descubrirlo aquí.

---

## 8. Claves de API

GitHub Actions sube el modelo a Object Storage con la CLI de OCI, que necesita
una clave de API.

**Consola → Perfil → My profile → API Keys → Add API Key → Generate API key pair**

1. **Descargar la clave privada** (`.pem`). No se puede volver a descargar.
2. Click en *Add*.
3. OCI muestra un recuadro de configuración. **Copiarlo entero**: contiene el
   user OCID, el tenancy OCID y el fingerprint.

```
[DEFAULT]
user=ocid1.user.oc1..aaaa...
fingerprint=12:34:56:...
tenancy=ocid1.tenancy.oc1..aaaa...
region=sa-saopaulo-1
key_file=<path to your private keyfile>
```

> 📝 **Anotar:** `OCI_CLI_USER`, `OCI_CLI_FINGERPRINT`, `OCI_CLI_TENANCY`,
> `OCI_REGION` y el contenido completo del `.pem`.

---

## 9. Configurar GitHub

**Repositorio → Settings → Secrets and variables → Actions**

### 9.1 Secrets (pestaña *Secrets*)

Se enmascaran en los logs. Aquí va todo lo sensible.

| Secret | Valor | De dónde sale |
|---|---|---|
| `OCIR_USERNAME` | `<namespace>/[dominio/]<usuario>` | Paso 7.3 |
| `OCIR_AUTH_TOKEN` | El auth token | Paso 7.2 |
| `OCI_CLI_USER` | `ocid1.user.oc1..…` | Paso 8 |
| `OCI_CLI_TENANCY` | `ocid1.tenancy.oc1..…` | Paso 8 |
| `OCI_CLI_FINGERPRINT` | `12:34:56:…` | Paso 8 |
| `OCI_CLI_KEY_CONTENT` | Contenido completo del `.pem` | Paso 8 |
| `OCI_SSH_PRIVATE_KEY` | Contenido de `~/.ssh/techmind_deploy` | Paso 3 |

Para las dos claves privadas, pegar el archivo **entero**, incluidas las líneas
`-----BEGIN …-----` y `-----END …-----`, y respetando el salto de línea final.
Una clave sin el salto final es el error silencioso más frecuente de este paso.

```bash
# Copiar al portapapeles sin errores (macOS)
pbcopy < ~/.ssh/techmind_deploy
```

### 9.2 Variables (pestaña *Variables*)

No son sensibles y quedan visibles en los logs, lo cual ayuda a depurar.

| Variable | Valor de ejemplo |
|---|---|
| `OCIR_REGISTRY` | `gru.ocir.io` |
| `OCI_NAMESPACE` | `grxxxxxxxxxx` |
| `OCI_REGION` | `sa-saopaulo-1` |
| `OCI_BUCKET_NAME` | `techmind-models` |
| `OCI_HOST` | La IP pública de la VM |
| `OCI_SSH_USER` | `opc` |
| `TARGET_PLATFORM` | `linux/arm64` (o `linux/amd64` si el shape es x86) |

### 9.3 Environment (opcional pero recomendado)

**Settings → Environments → New environment** → `produccion`

En *Deployment protection rules*, activar **Required reviewers** y agregarse.
Así ningún merge a `main` toca producción sin que alguien apruebe. Durante la
demo, esto evita que un commit de último minuto tumbe el entorno que se está
mostrando.

---

## 10. Primer despliegue

```bash
git add .
git commit -m "feat(devops): contenerizacion, CI/CD e integracion con OCI"
git push origin main
```

Seguirlo en **Actions → CD - Despliegue en OCI**.

El primer despliegue tarda 8–12 minutos: no hay caché de capas y construir
scikit-learn para ARM es lento. Los siguientes bajan a 3–4 minutos.

### Verificar

```bash
# Desde tu máquina
./scripts/smoke-test.sh <IP>

# O directamente
curl http://<IP>:8080/v3/api-docs
```

Swagger UI queda en `http://<IP>:8080/swagger-ui/index.html`.

---

## 11. Operación diaria

### Desplegar

Merge a `main`. No hay más.

### Rollback

**Actions → CD → Run workflow →** en *image_tag*, poner el SHA de un commit
anterior.

También ocurre solo: si los healthchecks del despliegue nuevo no pasan, el
workflow revierte al `IMAGE_TAG` anterior sin intervención.

### Ver el estado en la VM

```bash
ssh -i ~/.ssh/techmind_deploy opc@<IP>
cd /opt/techmind

docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f --tail=100
```

### Reiniciar sin redesplegar

```bash
cd /opt/techmind
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart
```

### Forzar la recarga del modelo

El modelo se descarga al arrancar el contenedor. Tras subir uno nuevo al bucket:

```bash
cd /opt/techmind
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  up -d --force-recreate ml-service
```

---

## 12. Diagnóstico de problemas

### `exec format error` al arrancar un contenedor

La imagen se construyó para una arquitectura distinta a la de la VM. Revisar la
variable `TARGET_PLATFORM`: debe ser `linux/arm64` para shapes A1, `linux/amd64`
para E2/E3/E4.

### El despliegue funciona pero `curl` desde fuera no responde

Falta una de las dos capas de firewall. Comprobar en orden:

```bash
# 1. ¿Responde desde dentro de la VM?
ssh opc@<IP> 'curl -s -o /dev/null -w "%{http_code}" localhost:8080/v3/api-docs'
# Si esto da 000, el problema es del contenedor, no de la red.

# 2. ¿Está abierto el firewall del SO?
ssh opc@<IP> 'sudo firewall-cmd --list-ports'
# Debe aparecer 8080/tcp

# 3. Si ambos están bien, falta la Security List de la VCN (paso 4.1).
```

### `ml-service` en estado *unhealthy*

```bash
ssh opc@<IP> 'curl -s localhost:8000/health'
```

El campo `origen_modelo` trae el error exacto de carga. Causas habituales:

- **`ServiceError … NotAuthenticated`** → falta el dynamic group o la policy
  (paso 6), o la regla no coincide con el compartment de la instancia.
- **`ObjectNotFound`** → el objeto `model.joblib` no está en el bucket. Se sube
  en el job `publicar-modelo` del CD; revisar si ese job pasó.
- **`OCI_NAMESPACE` vacío** → falta en `/opt/techmind/.env`.

### El backend no arranca

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs backend
```

- **`Could not resolve placeholder 'DB_USERNAME'`** → falta esa variable en el
  `.env` de la VM. El perfil `prod` no le da valor por defecto.
- **`OutOfMemoryError`** → el límite de memoria del compose es muy bajo para el
  shape, o el shape es un `E2.1.Micro` de 1 GB.

### El backend está *unhealthy* pero responde bien

Es probable que el equipo haya protegido `/v3/api-docs` en `SecurityConfig`. Dos
salidas: dejar esa ruta pública, o cambiar la línea `HEALTHCHECK` de
`backend/Dockerfile` por un endpoint que sí sea público.

### `permission denied` en `/var/run/docker.sock` durante el CD

El usuario no está en el grupo `docker`, o se agregó pero no se reinició la
sesión.

```bash
ssh opc@<IP> 'sudo usermod -aG docker opc'
```

Y reiniciar la instancia desde la consola para que se aplique a las conexiones
SSH nuevas.

### `unauthorized` al hacer push a OCIR

Casi siempre es el formato de `OCIR_USERNAME` (falta el namespace delante, o
falta el segmento del identity domain), o el auth token se copió mal. Probar el
`docker login` a mano desde tu máquina (paso 7.3): si falla ahí, falla igual en
Actions.

---

## Servicios de OCI utilizados

Para la presentación del hackathon:

| Servicio | Uso en el proyecto |
|---|---|
| **Object Storage** | Almacenamiento versionado de los artefactos del modelo y sus métricas |
| **Compute** | VM ARM Always Free que aloja los dos contenedores |
| **Container Registry (OCIR)** | Registro privado de las imágenes Docker |
| **IAM** (dynamic groups + policies) | Autenticación de la VM sin credenciales guardadas |
| **Virtual Cloud Network** | Red y control de acceso por Security List |

El brief exige al menos un servicio de OCI. Aquí se usan cinco, y de forma
funcional: sin Object Storage no hay modelo que servir, y sin OCIR no hay
imágenes que desplegar.
