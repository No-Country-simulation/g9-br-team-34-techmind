import re

import joblib
from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from scipy.sparse import hstack


app = FastAPI(
    title="API Clasificador de Contenido Técnico",
    description="Servicio de inferencia conforme al contrato REST TM-006",
    version="1.0.0"
)



@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request,
    exc: RequestValidationError
):
    errores = []

    for error in exc.errors():
        campo = " -> ".join(str(x) for x in error["loc"] if x != "body")
        errores.append(f"{campo}: {error['msg']}")

    return JSONResponse(
        status_code=400,
        content={
            "error": "entrada_invalida",
            "detalle": "; ".join(errores)
        }
    )


app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

MODELO_CARGADO = False
try:
    modelo = joblib.load("modelo_clasificador.joblib")
    vect_titulo = joblib.load("tfidf_titulo.joblib")
    vect_texto = joblib.load("tfidf_texto.joblib")
    MODELO_CARGADO = True
except Exception:
    modelo = vect_titulo = vect_texto = None

PESO_TITULO = 0.5

NORMALIZACIONES = {
    r"\bc\+\+": "cplusplus",
    r"\bc#": "csharp",
    r"\bf#": "fsharp",
    r"\.net\b": "dotnet",
    r"\bnode\.js\b": "nodejs",
    r"\bvue\.js\b": "vuejs",
    r"\breact\.js\b": "reactjs",
    r"\bangular\.js\b": "angularjs",
    r"\bci\s*/\s*cd\b": "cicd",
}

def limpiar_texto_api(texto:str)->str:
    texto = texto.lower()
    for p,r in NORMALIZACIONES.items():
        texto = re.sub(p,r,texto)
    texto = re.sub(r"http\S+|www\.\S+"," ",texto)
    texto = re.sub(r"[^\w\sáéíóúñü]"," ",texto)
    return re.sub(r"\s+"," ",texto).strip()


class PredictRequest(BaseModel):
    titulo: str
    texto: str

class PredictResponse(BaseModel):
    categoria:str
    probabilidad:float
    informacion_adicional:list[str]

@app.get("/")
def inicio():
    return {"mensaje":"API de clasificación funcionando correctamente"}

@app.get("/health")
def health():
    if MODELO_CARGADO:
        return {"estado":"ok","modelo_cargado":True}
    raise HTTPException(status_code=503,detail={"estado":"degradado","modelo_cargado":False})

@app.post("/predict",response_model=PredictResponse)
def predict(datos:PredictRequest):
    if not MODELO_CARGADO:
        raise HTTPException(status_code=503,detail={
            "error":"modelo_no_cargado",
            "detalle":"Descarga del artefacto desde OCI Object Storage en curso."
        })
    titulo=limpiar_texto_api(datos.titulo)
    texto=limpiar_texto_api(datos.texto)
    if not texto:
        raise HTTPException(status_code=422,detail={
            "error":"texto_vacio_tras_limpieza",
            "detalle":"El texto no contiene tokens útiles luego del preprocesamiento."
        })
    try:
        vt=vect_titulo.transform([titulo])*PESO_TITULO
        vx=vect_texto.transform([texto])
        vec=hstack([vt,vx])
        categoria=modelo.predict(vec)[0]
        prob=round(float(modelo.predict_proba(vec)[0].max()),4)
        nombres=list(vect_titulo.get_feature_names_out())+list(vect_texto.get_feature_names_out())
        d=vec.toarray()[0]
        idx=d.argsort()[::-1][:20]
        palabras=[nombres[i] for i in idx if d[i]>0]
        return {

            "categoria": categoria,
            "probabilidad": prob,
            "informacion_adicional": palabras if palabras is not None else []
}
    except Exception:
        raise HTTPException(status_code=500,detail={
            "error":"error_interno_modelo",
            "detalle":"Error al ejecutar el modelo."
        })


#py -3.12 -m uvicorn app:app --reload
