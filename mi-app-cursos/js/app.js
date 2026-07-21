// Base de datos simulada de cursos
const cursos = [
    { nombre: "Python para Principiantes", tema: "Python", idioma: "Español", nivel: "Principiante" },
    { nombre: "Advanced Python Mechanics", tema: "Python", idioma: "Inglés", nivel: "Avanzado" },
    { nombre: "Introducción al Diseño Web", tema: "Diseño Web", idioma: "Español", nivel: "Principiante" },
    { nombre: "CSS Grid y Flexbox Avanzado", tema: "Diseño Web", idioma: "Español", nivel: "Avanzado" },
    { nombre: "Machine Learning con Python", tema: "Machine Learning", idioma: "Español", nivel: "Intermedio" },
    { nombre: "Deep Learning Foundations", tema: "Machine Learning", idioma: "Inglés", nivel: "Avanzado" }
];

// Lógica del Árbol de Decisión para filtrar e imprimir en pantalla
function filtrarCursos() {
    const filtroTema = document.getElementById("tema").value;
    const filtroIdioma = document.getElementById("idioma").value;
    const filtroNivel = document.getElementById("nivel").value;
    const contenedor = document.getElementById("resultados");
    
    // Limpiar los resultados anteriores de la pantalla
    contenedor.innerHTML = ""; 

    // El árbol evalúa secuencialmente cada curso mediante los filtros
    const cursosFiltrados = cursos.filter(curso => {
        // Nodo de decisión 1: ¿Coincide el tema elegido?
        if (filtroTema !== "Todos" && curso.tema !== filtroTema) return false;
        
        // Nodo de decisión 2: ¿Coincide el idioma elegido?
        if (filtroIdioma !== "Todos" && curso.idioma !== filtroIdioma) return false;
        
        // Nodo de decisión 3: ¿Coincide el nivel elegido?
        if (filtroNivel !== "Todos" && curso.nivel !== filtroNivel) return false;
        
        // Si no fue descartado por ningún nodo, el curso pasa el filtro del árbol
        return true;
    });

    // Validar si el árbol quedó vacío después del filtrado
    if (cursosFiltrados.length === 0) {
        contenedor.innerHTML = `<div class="no-results">No se encontraron cursos que coincidan con la selección.</div>`;
        return;
    }

    // Construir y renderizar las tarjetas de resultados en el HTML
    cursosFiltrados.forEach(curso => {
        const tarjeta = document.createElement("div");
        tarjeta.className = "card";
        tarjeta.innerHTML = `
            <h3>${curso.nombre}</h3>
            <span class="badge">${curso.tema}</span>
            <span class="badge">${curso.idioma}</span>
            <span class="badge">${curso.nivel}</span>
        `;
        contenedor.appendChild(tarjeta);
    });
}

// Inicializar la carga de la aplicación mostrando todos los cursos disponibles de entrada
filtrarCursos();
