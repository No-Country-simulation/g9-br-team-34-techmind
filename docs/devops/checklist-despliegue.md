# Checklist previo al despliegue

Qué falta para que TechMind esté corriendo en OCI. El detalle de cada paso está
en [despliegue-oci.md](despliegue-oci.md); esto es la lista para ir tachando.

---

## Estado actual

| Parte | Estado |
|---|---|
| Contenerización (Dockerfiles, compose) | ✅ Escrita |
| CI/CD (workflows de GitHub Actions) | ✅ Escrito |
| Scripts de operación y documentación | ✅ Escritos |
| Servicio de inferencia (andamio) | ✅ Funcional |
| **Recursos en OCI** | ❌ **No creados** |
| **Secrets y variables en GitHub** | ❌ **No cargados** |
| **Código subido al repositorio** | ❌ **Sin commitear** |
| Endpoint del backend (`POST /api/v1/contenidos`) | ⏳ De tus compañeros |
| Dataset real de Ciencia de Datos | ⏳ Del equipo de DS |

Nada de la infraestructura se ha ejecutado todavía: está escrita y validada
estáticamente, pero no probada contra OCI.

---

## Bloque 1 — En tu máquina (5 min)

- [ ] **Desbloquear git.** Ahora mismo falla con *"You have not agreed to the
      Xcode license agreements"*. En una terminal:
      ```bash
      sudo xcodebuild -license
      ```
      Sin esto no puedes ni commitear ni hacer push, así que nada de lo demás
      puede empezar.

- [ ] **Commitear y subir el trabajo de DevOps.**
      ```bash
      git checkout -b feature/deploy-oci   # si no estás ya en ella
      git add .
      git commit -m "feat(devops): contenerizacion, CI/CD e integracion con OCI"
      git push origin feature/deploy-oci
      ```
      Verifica antes que `.env` **no** aparezca en `git status`: está en
      `.gitignore`, pero conviene comprobarlo porque contiene credenciales.

> No hace falta instalar Docker ni Java en tu Mac. La construcción ocurre en los
> runners de GitHub. Instalarlos solo sirve si quieres probar en local con
> `make up` antes de subir, que es recomendable pero no bloqueante.

---

## Bloque 2 — Crear los recursos en OCI (45–60 min)

Todo esto es manual y solo se hace una vez. Sigue
[despliegue-oci.md](despliegue-oci.md) en orden.

- [ ] Cuenta de OCI creada y **home region** elegida (São Paulo o Santiago para
      Latinoamérica) — §1.1
- [ ] Compartment `techmind` creado → anotar su **OCID** — §1.2
- [ ] **Namespace** del tenancy anotado — §1.3
- [ ] Bucket `techmind-models` creado, privado — §2
- [ ] Instancia `techmind-vm` creada (`VM.Standard.A1.Flex`, 4 OCPU / 24 GB,
      Oracle Linux 9) → anotar la **IP pública** — §3
- [ ] Par de claves SSH generado **solo para el despliegue** — §3
- [ ] Puerto 8080 abierto en la **Security List de la VCN** — §4.1
- [ ] `provision-vm.sh` ejecutado en la VM — §5
- [ ] `/opt/techmind/.env` completado a mano en la VM — §5.1
- [ ] Dynamic group `techmind-instances` + policy creados — §6
- [ ] **Auth Token** de OCIR generado (se muestra una sola vez) — §7.2
- [ ] `docker login` a OCIR probado a mano desde tu máquina — §7.3
- [ ] **API Key** generada y el `.pem` descargado — §8

---

## Bloque 3 — Configurar GitHub (10 min)

**Settings → Secrets and variables → Actions**

### Secrets (7)

- [ ] `OCIR_USERNAME`
- [ ] `OCIR_AUTH_TOKEN`
- [ ] `OCI_CLI_USER`
- [ ] `OCI_CLI_TENANCY`
- [ ] `OCI_CLI_FINGERPRINT`
- [ ] `OCI_CLI_KEY_CONTENT`
- [ ] `OCI_SSH_PRIVATE_KEY`

### Variables (7)

- [ ] `OCIR_REGISTRY`
- [ ] `OCI_NAMESPACE`
- [ ] `OCI_REGION`
- [ ] `OCI_BUCKET_NAME`
- [ ] `OCI_HOST`
- [ ] `OCI_SSH_USER`
- [ ] `TARGET_PLATFORM` → `linux/arm64` (o `linux/amd64` si tuviste que usar un
      shape x86)

> El error más común de este bloque son las dos claves privadas. Hay que pegar
> el archivo **entero**, con las líneas `-----BEGIN-----` y `-----END-----`, y
> con el salto de línea final. Usa `pbcopy < ~/.ssh/techmind_deploy`.

---

## Bloque 4 — Primer despliegue

- [ ] Merge de la rama a `main`
- [ ] Seguir **Actions → CD - Despliegue en OCI** (8–12 min la primera vez)
- [ ] Verificar: `./scripts/smoke-test.sh <IP>`

---

## Lo que NO depende de ti

Estas dos cosas faltan para el MVP completo, pero son de tus compañeros:

**Backend.** `ContenidoController`, `ContenidoService` y `ModeloInferenciaClient`
son clases vacías. El despliegue va a funcionar igualmente — los contenedores
arrancan sanos y Swagger responde —, pero el endpoint `POST /api/v1/contenidos`
que pide el brief todavía no existe. Cuando lo implementen, se despliega solo
con hacer merge.

Mientras tanto, el modelo ya se puede demostrar llamando directamente al
`ml-service` en el puerto 8000 (en local; en producción no está expuesto).

**Ciencia de Datos.** El dataset de `ml-service/train/dataset.csv` es un andamio
mío de 56 documentos para que la canalización funcione de punta a punta. Cuando
entreguen el suyo, se reemplaza el CSV y el CD lo reentrena y lo publica solo.

---

## Sobre el free tier

- **`VM.Standard.A1.Flex` da "Out of host capacity" a menudo** en las regiones
  populares. Reintenta más tarde o en otra availability domain. Si no hay forma,
  usa `VM.Standard.E2.1.Micro` (x86, 1 GB de RAM) y cambia `TARGET_PLATFORM` a
  `linux/amd64`. Con 1 GB hay que bajar los límites de memoria de
  `docker-compose.prod.yml`, porque la JVM más scikit-learn no entran.
- Los primeros **30 días son trial** con crédito. Al terminar, la cuenta pasa a
  Always Free y **los recursos que no sean Always Free se eliminan**. Verifica
  que la instancia tenga la etiqueta *Always Free eligible* al crearla.
- Object Storage y OCIR entran holgadamente en los límites gratuitos para el
  tamaño de este proyecto.
- El tráfico de salida gratuito es de 10 TB/mes. Irrelevante aquí.

---

## Después del primer despliegue: ¿es automático?

Sí. Cada merge a `main` dispara el ciclo completo sin intervención:

```
merge a main → pruebas → entrena y sube el modelo → construye imágenes ARM
             → sube a OCIR → despliega por SSH → verifica → (revierte si falla)
```

Tres matices:

1. **Solo `main`.** Los push a otras ramas ejecutan CI, no CD. Es deliberado:
   una rama de trabajo no debe poder tocar producción.
2. **Si activas *Required reviewers*** en el environment `produccion`
   (§9.3 del runbook), el despliegue se queda esperando aprobación. Recomendado
   durante la demo, para que un commit de último minuto no tumbe lo que estás
   mostrando.
3. **Si las pruebas fallan, no se despliega nada.** El job `verificar` corre
   antes que todo lo demás.
