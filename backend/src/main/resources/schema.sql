CREATE TABLE IF NOT EXISTS contenidos_analizados (
    id UUID DEFAULT RANDOM_UUID() NOT NULL PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    texto VARCHAR(10000) NOT NULL,
    categoria VARCHAR(50) NOT NULL,
    probabilidad DOUBLE NOT NULL,
    fecha_procesamiento TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_contenido_categoria ON contenidos_analizados(categoria);

CREATE TABLE IF NOT EXISTS contenido_palabras_clave (
    contenido_id UUID NOT NULL,
    palabra_clave VARCHAR(100) NOT NULL,
    FOREIGN KEY (contenido_id) REFERENCES contenidos_analizados(id)
);
