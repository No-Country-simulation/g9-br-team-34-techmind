# G9-LATAM-Team-34-TechMind

Solución para la organización inteligente de contenido técnico mediante técnicas de Ciencia de Datos. La aplicación procesa documentos técnicos, identifica información relevante y expone los resultados a través de una API REST en formato JSON, integrándose con Oracle Cloud Infrastructure (OCI).

---

## Descripción

El proyecto tiene como objetivo facilitar la organización, consulta y reutilización de contenido técnico, permitiendo transformar grandes volúmenes de información en una base de conocimiento estructurada.

La solución recibe contenido técnico como entrada y aplica técnicas de procesamiento de texto y Ciencia de Datos para generar información enriquecida que puede ser consumida por otras aplicaciones.

Entre las capacidades que puede ofrecer la solución se encuentran:

- Clasificación temática del contenido.
- Extracción de información relevante.
- Identificación de palabras clave.
- Agrupación de documentos similares.
- Recomendación de contenidos relacionados.
- Organización automática de bases de conocimiento.

Todos los resultados son entregados mediante una API REST utilizando formato JSON.

---

## Arquitectura

```
                 Documento Técnico
                        │
                        ▼
                 API REST (Backend)
                        │
                        ▼
           Modelo de Ciencia de Datos
                        │
                        ▼
        Clasificación / Procesamiento
                        │
                        ▼
                 Respuesta JSON
                        │
                        ▼
              Aplicaciones Cliente
```

---

## Tecnologías

### Ciencia de Datos

- Python
- Pandas
- Scikit-learn
- TF-IDF
- Técnicas de similitud textual

### Backend

- API REST
- JSON

### Infraestructura

- Oracle Cloud Infrastructure (OCI)

Servicios OCI sugeridos:

- Object Storage
- Compute
- Functions
- Base de datos (opcional)

---

## Estructura del proyecto

```
.
├── data/
├── model/
├── notebooks/
├── api/
├── docs/
├── README.md
└── requirements.txt
```

---

## Flujo de funcionamiento

1. El cliente envía un contenido técnico a la API.
2. La API valida la solicitud.
3. El modelo procesa el texto.
4. Se identifica información relevante.
5. La API devuelve los resultados en formato JSON.

---

## Endpoint principal

### POST /contenido

Procesa un contenido técnico y devuelve la información obtenida por el modelo.

### Solicitud

```json
{
    "titulo": "Introducción a Spring Boot",
    "texto": "En este contenido se presentan los conceptos básicos para la creación de APIs REST utilizando Java y Spring Boot."
}
```

### Respuesta

```json
{
    "categoria": "Backend",
    "probabilidad": 0.89,
    "informacion_adicional": [
        "Java",
        "Spring Boot",
        "API REST"
    ]
}
```

La estructura de la respuesta puede variar según el enfoque implementado por el equipo.

---

## Instalación

Clonar el repositorio.

```bash
git clone <url-del-repositorio>
```

Ingresar al proyecto.

```bash
cd proyecto
```

---

## Ejemplo de uso

### Clasificación de contenido

Entrada

```
Tutorial de Docker
```

Salida

```json
{
    "categoria": "DevOps"
}
```

---
### Organización de contenido

Entrada

```
Documentación técnica
```

Salida

```json
{
    "categoria": "...",
    "probabilidad": "...",
    "informacion_adicional": [...]
}
```

---

## Componentes del proyecto

### Notebook de Ciencia de Datos

Incluye:

- Exploración y limpieza de datos (EDA).
- Procesamiento de texto.
- Transformación de datos.
- Entrenamiento del modelo.
- Evaluación.
- Serialización del modelo.

---

### API REST

Incluye:

- Recepción de contenido.
- Procesamiento mediante el modelo.
- Respuesta JSON.
- Validación de entrada.
- Manejo de errores.

---

## Alcance del MVP

El proyecto implementa un servicio capaz de:

- Recibir contenido técnico.
- Procesarlo mediante un modelo de Ciencia de Datos.
- Generar información enriquecida.
- Exponer los resultados mediante una API REST.

---

## Licencia

Este proyecto fue desarrollado con fines académicos para el Hackathon, siguiendo los requisitos establecidos en la propuesta del desafío.
