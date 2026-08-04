# DevOps — TechMind

Documentación de la infraestructura, la contenerización y las canalizaciones de
CI/CD del proyecto.

| Documento | Para qué sirve |
|---|---|
| [checklist-despliegue.md](checklist-despliegue.md) | **Empieza por aquí:** qué falta para estar en producción |
| [ejecucion-local.md](ejecucion-local.md) | Levantar el proyecto completo en tu máquina |
| [despliegue-oci.md](despliegue-oci.md) | Runbook paso a paso para desplegar en Oracle Cloud |
| [arquitectura.md](arquitectura.md) | Cómo encajan las piezas y por qué |

---

## Qué hay montado

**Contenerización.** Dos imágenes Docker de construcción en dos etapas, ambas
corriendo como usuario sin privilegios y con healthcheck propio. La orquestación
es un `docker-compose.yml` para desarrollo y un `docker-compose.prod.yml` que lo
sobrescribe para producción.

**Integración continua** (`.github/workflows/ci.yml`). En cada push y cada pull
request: compila el backend con Maven, ejecuta las pruebas, pasa el linter y las
pruebas de Python, construye ambas imágenes y por último levanta el sistema
entero para interrogarlo con los tres ejemplos de uso del brief.

**Entrega continua** (`.github/workflows/cd.yml`). Al hacer merge a `main`:
entrena el modelo y lo sube a OCI Object Storage, publica las imágenes en OCIR y
actualiza los contenedores de la VM por SSH. Si los healthchecks no pasan,
revierte solo a la versión anterior.

**Integración con OCI.** Object Storage para los artefactos del modelo, Compute
para alojar la aplicación y OCIR como registro de imágenes. La VM se autentica
mediante *instance principal*, de modo que no hay ninguna clave privada guardada
en el servidor.

---

## Mapa de archivos

```
.
├── .github/workflows/
│   ├── ci.yml                    Integración continua
│   └── cd.yml                    Despliegue en OCI
│
├── backend/
│   ├── Dockerfile                Maven+JDK 17 → JRE 17 Alpine
│   └── .dockerignore
│
├── ml-service/                   Servicio de inferencia (Python)
│   ├── app/                      FastAPI: main, model, schemas, settings
│   ├── train/                    Entrenamiento y dataset semilla
│   ├── tests/                    Pruebas del contrato HTTP
│   ├── Dockerfile
│   ├── pyproject.toml            Configuración de ruff y pytest
│   ├── requirements.txt          Producción
│   └── requirements-dev.txt      Pruebas y linter
│
├── scripts/
│   ├── provision-vm.sh           Prepara la VM de OCI (se ejecuta una vez)
│   └── smoke-test.sh             Verifica un sistema ya levantado
│
├── docker-compose.yml            Desarrollo
├── docker-compose.prod.yml       Producción (sobrescribe el anterior)
├── .env.example                  Plantilla de configuración
└── Makefile                      Atajos: make up, make test, make smoke
```

---

## Lo mínimo para arrancar

```bash
make env     # crea .env a partir de .env.example
make up      # construye y levanta todo
make smoke   # verifica que responda
```

---

## Frontera con el resto del equipo

Nada de esto modifica el código de Backend ni de Ciencia de Datos.

- **No se tocó** `pom.xml`, ni las clases Java, ni los `.properties` del backend.
  Se puede seguir usando `./mvnw spring-boot:run` exactamente igual que antes.
- El `ml-service/` es un **andamio** para que la canalización completa se pueda
  demostrar de punta a punta desde ya. Ciencia de Datos reemplaza
  `train/dataset.csv` y, si quiere, `train/train.py`; lo único que debe
  respetarse es el formato del artefacto serializado, descrito en
  [arquitectura.md](arquitectura.md#contrato-del-artefacto-del-modelo).

### Dos puntos a confirmar con el equipo

1. **Las 7 categorías.** El dataset semilla usa: `Backend`, `Frontend`,
   `DevOps`, `Ciencia de Datos`, `Moviles`, `Bases de Datos`, `Seguridad`. Los
   DTO mencionan "las 7 categorías de TM-006 §0", documento al que no tengo
   acceso. Si la lista real es otra, hay que corregir la columna `categoria` del
   dataset.

2. **El healthcheck del backend** apunta a `/v3/api-docs`, que springdoc ya
   expone. Se eligió esa ruta para no tener que agregar `spring-boot-actuator`
   al `pom.xml`. Si al implementar `SecurityConfig` esa ruta queda protegida, hay
   que dejarla pública o cambiar el healthcheck del `backend/Dockerfile`, o el
   contenedor se marcará como *unhealthy* aun funcionando bien.
