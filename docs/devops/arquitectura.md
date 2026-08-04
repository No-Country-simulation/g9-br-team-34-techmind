# Arquitectura DevOps — TechMind

Cómo está montada la infraestructura y por qué se tomó cada decisión.

---

## Vista general

```
                          Internet
                              │
                              ▼
              ┌───────────────────────────────┐
              │   VCN Security List :8080     │   ← primera capa de firewall
              └───────────────┬───────────────┘
                              ▼
    ╔═════════════════════════════════════════════════╗
    ║   OCI Compute — VM.Standard.A1.Flex (ARM)       ║
    ║   Oracle Linux 9 · firewalld :8080              ║   ← segunda capa
    ║                                                 ║
    ║   ┌───────────────────────────────────────┐     ║
    ║   │  Red interna de Docker: techmind-net  │     ║
    ║   │                                       │     ║
    ║   │   backend :8080  ──HTTP──►  ml-service│     ║
    ║   │   (Spring Boot)             :8000     │     ║
    ║   │        │                    (FastAPI) │     ║
    ║   │        ▼                        │     │     ║
    ║   │   volumen H2                    │     │     ║
    ║   └─────────────────────────────────┼─────┘     ║
    ╚═════════════════════════════════════╪═══════════╝
                                          │ descarga al arrancar
                                          ▼
                          ┌───────────────────────────┐
                          │  OCI Object Storage       │
                          │  bucket techmind-models   │
                          │    model.joblib           │
                          │    modelos/model-<sha>.…  │
                          └───────────────────────────┘
```

Solo el backend publica un puerto. El `ml-service` es alcanzable únicamente
desde dentro de la red de Docker: no tiene autenticación propia, así que
exponerlo sería regalar acceso directo al modelo.

---

## Servicios

### `backend` — API REST (Java 17 / Spring Boot 3.3)

Es la cara pública del sistema. Recibe el contenido técnico, lo valida, se lo
pasa al servicio de inferencia y devuelve el JSON al cliente.

La imagen se construye en dos etapas. La primera compila con Maven sobre un JDK
completo; la segunda parte de `eclipse-temurin:17-jre-alpine` y solo recibe el
`.jar`. Compilar y ejecutar en la misma imagen dejaría el JDK, Maven y el
repositorio `~/.m2` dentro del contenedor de producción, multiplicando el tamaño
y la superficie de ataque a cambio de nada.

Dentro del contenedor la JVM arranca con `-XX:MaxRAMPercentage=75.0`. Sin ese
límite la JVM dimensiona el heap según la memoria de la máquina anfitriona y no
según la asignada al contenedor, y en una VM pequeña el kernel termina matando
el proceso por consumo de memoria.

### `ml-service` — Servicio de inferencia (Python 3.11 / FastAPI)

Carga el modelo serializado y lo expone por HTTP. Dos endpoints: `GET /health` y
`POST /predict`.

El modelo se entrena en tiempo de **build** de la imagen, nunca al arrancar el
contenedor. Entrenar al arrancar haría que el tiempo de despliegue dependiera
del tamaño del dataset y que dos réplicas del mismo servicio pudieran acabar
sirviendo modelos distintos.

---

## Contrato del artefacto del modelo

Es la frontera entre DevOps y Ciencia de Datos. `train/train.py` produce un
`.joblib` y `app/model.py` lo lee. Mientras se respete este formato, Ciencia de
Datos puede cambiar el algoritmo, el dataset o el preprocesamiento sin tocar
nada de la infraestructura.

```python
joblib.dump({
    "pipeline":   Pipeline,   # con pasos "tfidf" y "clf", y método predict_proba
    "categorias": list[str],  # catálogo completo de categorías
    # Metadatos, opcionales para el servicio pero útiles para rastrear
    # qué modelo exacto está en producción:
    "entrenado_en":     str,   # ISO 8601
    "n_documentos":     int,
    "exactitud_prueba": float,
}, "models/model.joblib", compress=3)
```

Lo que `app/model.py` exige de verdad:

- La clave `pipeline` responde a `predict_proba(list[str]) -> matriz`.
- `pipeline.named_steps["clf"].classes_` da el orden real de las columnas de esa
  matriz.
- `pipeline.named_steps["tfidf"]` responde a `transform()` y a
  `get_feature_names_out()` — de ahí salen las palabras clave.
- La clave `categorias` es la lista completa de etiquetas.

Si Ciencia de Datos cambia a un modelo sin `predict_proba` o sin vectorizador
TF-IDF (por ejemplo, embeddings), hay que ajustar `ClassifierModel`. El resto de
la infraestructura no se entera.

---

## Cómo se conectan los servicios

El backend recibe `INFERENCE_SERVICE_URL=http://ml-service:8000`. `ml-service` es
el nombre DNS que Docker resuelve dentro de la red del compose. **Nunca
`localhost`**: dentro de un contenedor, `localhost` es ese mismo contenedor, no
la máquina anfitriona ni el otro servicio.

El backend no arranca hasta que el `ml-service` reporta estar sano:

```yaml
depends_on:
  ml-service:
    condition: service_healthy
```

Con `service_started` en vez de `service_healthy`, el backend arrancaría mientras
el modelo todavía se está cargando y las primeras peticiones fallarían con error
de conexión.

`GET /health` devuelve **503** si el modelo no cargó. Eso es lo que encadena todo
el mecanismo: 503 → healthcheck de Docker falla → contenedor *unhealthy* →
`depends_on` no se cumple → el backend no arranca → `docker compose up --wait`
retorna error → el despliegue se revierte.

---

## Configuración

Toda la configuración entra por variables de entorno. La misma imagen corre en
local, en CI y en OCI sin reconstruirse: cambiar de entorno es cambiar
variables, nunca recompilar.

| Origen | Contenido | Dónde vive |
|---|---|---|
| `.env` (local) | Puertos, perfil, credenciales de desarrollo | Máquina de cada persona, ignorado por git |
| `.env` (VM) | Credenciales de BD, API key, namespace de OCI | `/opt/techmind/.env`, permisos 600 |
| GitHub Secrets | Claves de OCI, token de OCIR, clave SSH | Ajustes del repositorio |
| GitHub Variables | Región, namespace, host, registro (no sensibles) | Ajustes del repositorio |

La distinción entre *secrets* y *variables* importa: los secrets se enmascaran
en los logs de Actions, las variables no. Un OCID de tenancy en los logs es
ruido; una clave privada en los logs es un incidente.

---

## Canalización de CI

```
push / pull request
        │
        ├── backend      : mvnw verify        ─┐
        ├── ml-service   : ruff + train + pytest ├── en paralelo
        │                                      ─┘
        ▼
    docker  : construye ambas imágenes + valida el compose
        ▼
  smoke-test: levanta el sistema y ejecuta los 3 ejemplos del brief
```

Los tres primeros jobs son independientes y corren en paralelo. El de imágenes
espera: construir un contenedor con código que no compila es tiempo de runner
desperdiciado.

El `smoke-test` ejecuta los tres ejemplos de uso del brief contra el sistema
real, en cada corrida. Es documentación que no puede quedar desactualizada,
porque si dejara de funcionar el check se pone rojo.

---

## Canalización de CD

```
merge a main
        │
    verificar : pruebas del backend + lint y pruebas del ml-service
        │
        ├── publicar-modelo   : entrena → sube a Object Storage  ─┐
        ├── publicar-imagenes : construye ARM → sube a OCIR       ─┘ en paralelo
        ▼
    desplegar : scp de los compose → ssh → docker compose pull + up --wait
        ▼
   comprobar : curl desde el runner (no desde la VM)
        │
        └── si falla → revierte al IMAGE_TAG anterior
```

**Por qué el CD repite las pruebas que ya hizo el CI.** Los dos workflows son
independientes: GitHub no encadena uno con otro por el hecho de que ambos
escuchen `main`. Sin el job `verificar`, un merge con las pruebas en rojo
llegaría igual a producción, porque los jobs de publicación solo compilan. Son
2–3 minutos de runner a cambio de no desplegar una versión rota.

**Compilación cruzada a ARM.** El runner de GitHub es x86_64 y la VM Always Free
es ARM (Ampere A1), así que `publicar-imagenes` configura QEMU antes de
construir. Sin ese paso el build para `linux/arm64` falla en el primer `RUN` del
Dockerfile. Los pasos emulados son lentos: `pip install` no lo nota porque baja
wheels precompiladas para aarch64, pero el entrenamiento del modelo sí corre
emulado y suma un par de minutos.

Tres decisiones más que vale la pena señalar:

**Las imágenes se etiquetan con el SHA del commit, no con `latest`.** `latest`
sirve para saber cuál es la última, pero no para saber qué código está
corriendo ni para volver atrás. Con el SHA, un rollback es cambiar una variable.

**El modelo se sube dos veces**: como `modelos/model-<sha>.joblib`, que queda
como historial inmutable, y como `model.joblib`, que es el nombre fijo que lee el
servicio. Junto a cada modelo viajan sus métricas: sin ellas, dentro de un mes
nadie podrá decir qué tan bueno era el modelo que estaba en producción.

**La verificación final se hace desde el runner de GitHub y no por SSH.** Un
`curl localhost` dentro de la VM confirmaría que el contenedor responde, pero no
que el servicio sea accesible a través de la Security List y del firewall.
Comprobarlo desde fuera es lo que prueba que el sistema está realmente
publicado.

---

## Seguridad

- Ambos contenedores corren como **usuario sin privilegios** (uid 1000). Un
  proceso que no necesita ser root no debe serlo: si alguien logra ejecutar
  código dentro del contenedor, la diferencia entre uid 1000 y uid 0 es la
  diferencia entre un incidente contenido y uno que no lo está.
- La VM usa **instance principal** para autenticarse contra Object Storage. No
  hay ninguna clave privada guardada en el servidor, así que no hay nada que
  rotar ni que se pueda filtrar desde ahí.
- El despliegue por SSH **fija la huella del host** con `ssh-keyscan` antes de
  conectar. Usar `StrictHostKeyChecking=no` aceptaría cualquier servidor que
  respondiera y dejaría el despliegue abierto a un ataque de intermediario.
- El `ml-service` **no publica ningún puerto** en producción.
- **Rotación de logs** en dos niveles: por servicio en el compose y global en
  `/etc/docker/daemon.json`. Sin rotación, los logs crecen hasta llenar el disco
  y tumban la VM entera — es una de las formas más comunes de perder un servidor
  pequeño.

---

## Limitaciones conocidas

Cosas que en un proyecto real se resolverían y que aquí se dejaron fuera a
conciencia, por alcance de hackathon:

- **Sin TLS.** La API se sirve por HTTP plano. Lo correcto sería poner Caddy o
  nginx delante con un certificado de Let's Encrypt.
- **Una sola VM, sin réplicas.** Es un punto único de fallo; durante el
  redespliegue hay unos segundos de corte.
- **H2 en archivo como base de datos.** Persiste en un volumen, pero no es una
  base de producción. La migración natural sería a Autonomous Database de OCI.
- **Sin métricas ni alertas.** Solo hay healthchecks. Con `spring-boot-actuator`
  y OCI Monitoring se cubriría, pero eso requiere tocar el `pom.xml`, que no es
  alcance de DevOps en este equipo.
