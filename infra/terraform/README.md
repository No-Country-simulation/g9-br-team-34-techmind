# Infraestructura de TechMind en Terraform

Crea, con un solo comando, todo lo que el proyecto necesita en Oracle Cloud
Infrastructure:

| Recurso | Para qué |
|---|---|
| VCN, Internet Gateway, tabla de rutas, subred pública | la red donde vive la VM |
| Security List | abre 22 y 8080 (y 80/443 si vas a poner HTTPS) |
| Instancia `VM.Standard.A1.Flex` | donde corren los contenedores |
| Bucket privado de Object Storage | donde la canalización publica el modelo |
| Dynamic Group + Policy | permite a la VM leer el modelo **sin guardar credenciales** |

Reemplaza los pasos 2 al 8 del [runbook](../../docs/devops/despliegue-oci.md), que
son unos cuarenta minutos de consola web propensos a error.

---

## Antes de empezar

Necesitás dos cosas creadas a mano, porque son credenciales y no infraestructura:

**1. La clave SSH del despliegue**

```bash
ssh-keygen -t ed25519 -C "techmind-deploy" -f ~/.ssh/techmind_deploy -N ""
```

**2. Una API key de OCI** — Consola → *Identity & Security → Domains → Default
domain → Users* → tu usuario → **API Keys → Add API Key → Generate API Key
Pair**. Descargá la privada y guardala, por ejemplo, en
`~/.oci/techmind_api_key.pem`. Anotá el `user`, el `tenancy` y el `fingerprint`
que muestra la pantalla siguiente.

Es lo único que Terraform no puede crearse a sí mismo: es el huevo antes de la
gallina.

---

## Uso

No hace falta instalar Terraform: se puede correr en contenedor.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
# completar terraform.tfvars con tus valores
```

**Con Terraform instalado** (`brew install terraform`):

```bash
terraform init
terraform plan      # mirá qué va a crear ANTES de crearlo
terraform apply
```

**Sin instalar nada**, usando Docker:

```bash
alias tf='docker run --rm -it \
  -v "$PWD":/infra -w /infra \
  -v "$HOME/.oci":/root/.oci:ro \
  -v "$HOME/.ssh":/root/.ssh:ro \
  hashicorp/terraform:1.9'

tf init
tf plan
tf apply
```

> Si usás la forma con Docker, en `terraform.tfvars` las rutas tienen que ser
> las de **dentro** del contenedor: `/root/.oci/techmind_api_key.pem` y
> `/root/.ssh/techmind_deploy.pub`.

El `apply` tarda dos o tres minutos, casi todos esperando que la instancia
arranque.

---

## Qué hacer con las salidas

Al terminar, Terraform imprime los valores que necesitás para el resto del
despliegue, ya resueltos:

```bash
terraform output siguientes_pasos
```

Ese texto trae los comandos armados con tu IP incluida: cómo aprovisionar la
VM, qué poner en su `.env`, y las siete variables de GitHub listas para copiar.

Para una sola:

```bash
terraform output -raw github_OCI_HOST
```

---

## Cuando algo falla

**`Out of host capacity`** al crear la instancia. Es el problema más común y no
depende de vos: Ampere A1 está saturado en muchas regiones. En orden:

1. Cambiá `indice_dominio_disponibilidad` a `1`, después a `2`. La capacidad
   varía entre dominios de la misma región.
2. Probá en otro horario. De madrugada suele liberarse.
3. Plan B: pasá a `VM.Standard.E2.1.Micro` (está comentado en el `.tfvars.example`).
   Ojo, exige además cambiar `TARGET_PLATFORM` a `linux/amd64` y bajar los
   límites de memoria del compose de producción — ver el apéndice del runbook.

**`NotAuthenticated` del ml-service** al arrancar, aunque el `apply` salió bien.
Casi siempre es la variable `dominio_identidad`. Si tu tenancy usa identity
domains y la dejaste vacía, la policy se crea pero **no aplica a nadie**, sin dar
ningún error. Poné `"Default"` y volvé a aplicar.

**`401 NotAuthorized`** al ejecutar Terraform. La API key no coincide: revisá
que `fingerprint` corresponda a la clave de `ruta_clave_api`, y que el usuario
tenga permisos para crear recursos en ese compartment.

---

## Cosas que conviene saber antes de tocar esto

**El estado es sensible.** `terraform.tfstate` guarda en claro los OCID de tu
cuenta y todo lo que se creó. Está en `.gitignore` y **no debe versionarse**.
Si más de una persona va a aplicar, hay que mover el estado a un backend
remoto; con una sola persona, el archivo local alcanza.

**`terraform destroy` borra la máquina y la base de datos.** El volumen del
backend se va con la instancia. Antes de ejecutarlo, hacé el backup que está en
el runbook.

**La imagen del sistema está congelada a propósito.** El recurso de la instancia
tiene un `ignore_changes` sobre el ID de imagen. Sin eso, cada vez que Oracle
publica una imagen nueva de Oracle Linux, un `terraform apply` rutinario
propondría **recrear la máquina**: IP nueva, base de datos perdida, y la
variable `OCI_HOST` de GitHub apuntando a ninguna parte.

**La IP pública es efímera.** Si detenés y arrancás la instancia, cambia, y hay
que actualizar `OCI_HOST` en GitHub. Si necesitás que sea estable, hay que
reservarla, y una IP reservada sin usar tiene costo fuera de la capa gratuita.

**Terraform no configura la VM.** Solo la crea. Instalar Docker y dejar la
máquina lista sigue siendo trabajo de [`provision-vm.sh`](../../scripts/provision-vm.sh),
que se ejecuta después. Separarlos es deliberado: son dos responsabilidades
distintas y fallan por razones distintas.
