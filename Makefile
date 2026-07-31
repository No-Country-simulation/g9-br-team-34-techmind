# TechMind - atajos de desarrollo y operacion.
#
#   make            muestra esta ayuda
#   make up         levanta todo el sistema en local
#
# El objetivo de este archivo es que nadie del equipo tenga que recordar la
# invocacion exacta de docker compose con dos archivos -f y siete variables.

.DEFAULT_GOAL := help
.PHONY: help up down restart logs logs-backend logs-ml build rebuild ps \
        test test-ml test-backend lint train shell-ml shell-backend \
        smoke clean clean-all prod-up prod-down prod-logs prod-ps env

COMPOSE      := docker compose
COMPOSE_PROD := docker compose -f docker-compose.yml -f docker-compose.prod.yml

# --------------------------------------------------------------------------
# Ayuda
# --------------------------------------------------------------------------

help: ## Muestra esta ayuda
	@echo ""
	@echo "  TechMind - comandos disponibles"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'
	@echo ""

# --------------------------------------------------------------------------
# Desarrollo local
# --------------------------------------------------------------------------

env: ## Crea el archivo .env a partir del ejemplo (no sobrescribe el existente)
	@if [ -f .env ]; then \
		echo ".env ya existe, no se toca."; \
	else \
		cp .env.example .env; \
		echo ".env creado a partir de .env.example"; \
	fi

up: ## Levanta el sistema completo (backend + ml-service)
	$(COMPOSE) up --build --detach --wait
	@echo ""
	@echo "  API REST   -> http://localhost:8080"
	@echo "  Swagger    -> http://localhost:8080/swagger-ui/index.html"
	@echo "  Inferencia -> http://localhost:8000/docs"
	@echo "  Salud      -> http://localhost:8000/health"
	@echo ""

down: ## Detiene los contenedores (conserva los datos)
	$(COMPOSE) down

restart: down up ## Reinicia el sistema

build: ## Construye las imagenes sin levantar nada
	$(COMPOSE) build

rebuild: ## Reconstruye desde cero, ignorando la cache de Docker
	$(COMPOSE) build --no-cache

ps: ## Muestra el estado de los contenedores
	$(COMPOSE) ps

logs: ## Sigue los logs de todos los servicios
	$(COMPOSE) logs --follow --tail=100

logs-backend: ## Sigue los logs del backend
	$(COMPOSE) logs --follow --tail=100 backend

logs-ml: ## Sigue los logs del servicio de inferencia
	$(COMPOSE) logs --follow --tail=100 ml-service

shell-ml: ## Abre una shell dentro del contenedor del ml-service
	$(COMPOSE) exec ml-service /bin/bash

shell-backend: ## Abre una shell dentro del contenedor del backend
	$(COMPOSE) exec backend /bin/sh

# --------------------------------------------------------------------------
# Calidad
# --------------------------------------------------------------------------

test: test-ml test-backend ## Ejecuta todas las pruebas

test-ml: ## Pruebas del servicio de inferencia
	cd ml-service && pytest -v

test-backend: ## Pruebas del backend
	cd backend && ./mvnw -B test

lint: ## Analiza el codigo Python
	cd ml-service && ruff check app train tests

train: ## Entrena el modelo en local (genera ml-service/models/)
	cd ml-service && python -m train.train

smoke: ## Prueba el sistema levantado con los tres ejemplos del brief
	@bash scripts/smoke-test.sh

# --------------------------------------------------------------------------
# Produccion (se ejecutan DENTRO de la VM de OCI)
# --------------------------------------------------------------------------

prod-up: ## [en la VM] Levanta produccion
	$(COMPOSE_PROD) pull && $(COMPOSE_PROD) up -d --wait

prod-down: ## [en la VM] Detiene produccion
	$(COMPOSE_PROD) down

prod-ps: ## [en la VM] Estado de produccion
	$(COMPOSE_PROD) ps

prod-logs: ## [en la VM] Logs de produccion
	$(COMPOSE_PROD) logs --follow --tail=100

# --------------------------------------------------------------------------
# Limpieza
# --------------------------------------------------------------------------

clean: ## Detiene los contenedores y borra los artefactos locales
	$(COMPOSE) down --remove-orphans
	rm -rf ml-service/models ml-service/.pytest_cache ml-service/.ruff_cache
	find ml-service -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	cd backend && ./mvnw -B clean -q || true

clean-all: ## Como `clean`, pero ADEMAS borra los volumenes (se pierde la base de datos)
	$(COMPOSE) down --volumes --remove-orphans
	$(MAKE) clean
