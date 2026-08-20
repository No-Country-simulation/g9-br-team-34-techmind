# Base de datos Postgres (reemplazo de H2)

Guía de la base de datos Postgres gestionada de TechMind. Documenta **qué** se
provisionó, **dónde vive** cada credencial, **cómo validar** la conexión y
**qué issues** del sprint la consumen.

Reemplaza a la H2 embebida que usaba el backend. Corresponde al ticket
[S5-17 · Provisionar base de datos Postgres](https://github.com/No-Country-simulation/g9-br-team-34-techmind/issues/158)
y es la base para las mejoras de Postgres y autenticación.

---

## 1. Por qué Postgres gestionado

- **Motor real:** reemplaza la H2 embebida, que no es apta para más de un
  proceso ni para el flujo de autenticación.
- **Sin operar un servidor:** el equipo backend solo consume una connection
  string; la infraestructura de la base la administra el proveedor.
- **Habilita la autenticación:** la misma base aloja las tablas de usuarios,
  sesiones y organizaciones que consume el flujo de login.

---

## 2. Inventario provisionado

| Recurso | Valor |
|---|---|
| Motor | PostgreSQL 18.4 |
| Base de datos | `techmind` |
| Rol de aplicación | `techmind_app` (login + password) |
| Región | `us-east-2` |
| Entorno | una sola base (producción) |

**Decisión de entorno:** solo existe **una base, de producción**. No se creó una
base separada de desarrollo por tema de tiempo; desarrollo local, CI y
producción apuntan a la misma base. Si más adelante se quiere separar entornos,
el proveedor permite ramificar el proyecto sin duplicar infraestructura.

**Decisión de rol:** la aplicación usa el rol dedicado `techmind_app`, no el rol
administrador del proyecto. Es el principio de menor privilegio: si se rota su
password o se quiere auditar qué hace la app, no se toca al administrador.

---

## 3. Connection string

Formato (con **valores de ejemplo**, nunca reales):

```
postgresql://<usuario>:<password>@<host>/techmind?sslmode=require
```

El proveedor entrega dos variantes de la misma conexión:

| Variante | Uso |
|---|---|
| **Pooled** | Aplicación (Spring Boot / Hikari) |
| **Direct** | `psql`, DBeaver, migraciones |

> **El password real NO se versiona en ningún archivo del repositorio.** Se
> obtiene y se rota desde la consola de la base de datos gestionada. Aquí solo
> viven placeholders.

---

## 4. Dónde vive cada credencial

| Destino | Variable | Dónde se guarda | Estado |
|---|---|---|---|
| Desarrollo local (Maven) | `DATABASE_URL` | entorno del desarrollador: shell o configuración de run del IDE  |
| Orquestación local (docker compose) | `DATABASE_URL` | `.env` de la raíz (gitignored) |
| CI/CD (GitHub Actions) | `DATABASE_URL` (secret) | Settings → Secrets and variables → Actions |
| Producción (VM OCI) | `DATABASE_URL` | `/opt/techmind/.env` de la VM |

El placeholder versionado vive en dos `.env.example`:

- `.env.example` (raíz) — lo usa la orquestación con `docker compose`.
- `backend/.env.example` — lo usa el backend cuando corre suelto con Maven.

**Sobre el `.env`:** no es obligatorio para correr con Maven. Los archivos
`application-*.properties` ya leen variables de entorno con la sintaxis
`${VAR:default}`, así que el valor se toma directamente del shell o de la
configuración del IDE. El `.env` solo interviene cuando se usa `docker compose`,
que es quien lo lee para interpolar variables.

Las variables de H2 (`DB_USERNAME`, `DB_PASSWORD`, `DB_FILE_PATH`) se mantienen
por ahora marcadas como legado: se eliminan cuando S5-18 conecte Spring a
Postgres.

---

## 5. Validar la conexión

Desde cualquier máquina con acceso a Internet.

### Con `psql`

```bash
# Instalar el cliente (Ubuntu/Debian)
sudo apt install -y postgresql-client

# Conexión directa (la recomendada para psql), con el host y password reales
psql "postgresql://techmind_app:<password>@<host>/techmind?sslmode=require"
```

Dentro del prompt:

```sql
SELECT current_user, current_database(), version();
-- esperado: techmind_app | techmind | PostgreSQL 18.4 ...
```

### Con DBeaver / DataGrip / TablePlus

Crear una conexión **PostgreSQL** con:

| Campo | Valor |
|---|---|
| Host | el host directo de la base (lo entrega el proveedor) |
| Port | `5432` |
| Database | `techmind` |
| User | `techmind_app` |
| Password | el del rol |
| SSL | `require` |

Una conexión exitosa devuelve el árbol de la base `techmind`.

> **Nota sobre `channel_binding`:** algunos clientes (psql antiguos) rechazan el
> parámetro `channel_binding=require` que agrega el proveedor. En ese caso se
> omite y se deja solo `sslmode=require`.

---

## 6. Seguridad

- Los secrets de GitHub y el `.env` de la VM son los únicos lugares válidos.
- **`sslmode=require` siempre.** La conexión viaja cifrada.
- **Rol dedicado de menor privilegio** (`techmind_app`), separado del
  administrador.
- **Rotar el password** desde la consola de la base si alguna vez se filtra o
  deja el proyecto el miembro que lo conoce. No requiere migrar nada: el valor
  se actualiza en los destinos del punto 4.
- **Una sola base compartida** por todos los entornos por decisión del equipo.
  Si en el futuro se separan, se usa el branching del proveedor y se repite la
  tabla del punto 4 con una rama por entorno.

---

## 7. Referencias

- [Despliegue en OCI (runbook operativo)](despliegue-oci.md)
