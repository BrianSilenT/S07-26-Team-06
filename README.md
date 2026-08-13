# Benchmark de Madurez de Coordinación de Infraestructura

Benchmark anónimo que mide cómo los operadores de data centers coordinan
energía, cooling y workloads — y les devuelve, a cambio de completar un
diagnóstico de menos de 10 minutos, su posición relativa real frente al
resto de la industria. Cada respuesta alimenta además un dataset agregado
y anónimo que se vuelve más preciso con el tiempo.

## El problema que ataca

En los data centers modernos hay capacidad pagada y encendida que no
produce nada, porque las capas físicas (energía, cooling) y la capa
operativa (workloads) no se coordinan entre sí. Ese desperdicio —
*stranded capacity*— no tiene hoy ningún benchmark público que lo mida.
Este proyecto construye ese benchmark.

## Las 5 dimensiones que mide

| # | Dimensión | Pregunta que responde |
|---|---|---|
| 1 | **Visibilidad cross-layer** | ¿Tenés una vista unificada de energía, cooling y workloads? |
| 2 | **Atribución de fricción** | ¿En qué interfaz entre capas percibís más pérdida de capacidad? |
| 3 | **Latencia de coordinación** | Cuando cambia el workload, ¿qué tan rápido se ajustan cooling y energía? |
| 4 | **Auto-cuantificación** | ¿Sabés cuánta stranded capacity tenés? |
| 5 | **Bloqueantes** | Si supieras dónde está el problema, ¿qué te impediría resolverlo? |

Las dimensiones 1, 3 y 4 se scorean numéricamente (0-100) y tienen
percentil propio. Las dimensiones 2 y 5 son categóricas y se reportan
como distribución agregada, no como percentil.

## Arquitectura

```
┌─────────────────────┐      HTTP/JSON       ┌──────────────────────────┐      JDBC       ┌──────────────┐
│   Frontend (React)  │ ───────────────────▶ │  Backend (Spring Boot)   │ ──────────────▶ │   Supabase   │
│  Vite + lucide-react│ ◀─────────────────── │  Java 17 + JPA + Flyway  │ ◀────────────── │  (Postgres)  │
└─────────────────────┘                       └──────────────────────────┘                  └──────────────┘
                                                         │
                                                         ▼
                                                 PDF (OpenPDF)
                                              GET /reports/{id}/pdf
```

- **Frontend**: formulario de 6 pasos (segmento + 5 dimensiones) y dashboard
  de resultados. Sin estado en el servidor — todo el cálculo vive en el backend.
- **Backend**: expone 5 endpoints REST, calcula scoring/percentiles/insights,
  y genera el PDF del reporte.
- **Base de datos**: Postgres administrado por Supabase. Guarda respuestas
  de forma anónima (sin PII) y los rollups agregados.

## El motor de rebalanceo dinámico

El corazón del proyecto: el benchmark arranca calibrado 100% con una curva
de referencia pública (`public_reference_curves`, sembrada desde fuentes
reales — ver `SOURCES.md`), y a medida que se acumulan respuestas primarias
reales, su peso en el cálculo crece de forma suave:

```
peso_primario(N) = min( N / (N + k), max_primary_weight )
```

- `N` = respuestas primarias acumuladas por segmento y dimensión (no un
  contador global único).
- `k` = smoothing factor (default 50) — en `N = k`, el peso es 50/50.
- `max_primary_weight` = techo duro (default 0.95) para nunca depender
  100% de datos primarios.

El detalle completo de por qué esta fórmula y no un ratio fijo está
documentado en `backend/README.md`, sección "Motor de rebalanceo dinámico".

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Frontend | React + Vite, lucide-react |
| Backend | Java 17, Spring Boot 3.3, Spring Data JPA, Flyway |
| Base de datos | PostgreSQL (Supabase) |
| Generación de PDF | OpenPDF |
| Documentación de API | springdoc-openapi (Swagger UI en `/docs`) |

## Estructura del repositorio

```
.
├── backend/                    (o benchmark-engine/)
│   ├── src/main/java/com/benchmark/datacenter/
│   │   ├── controller/         # 5 endpoints REST
│   │   ├── service/            # scoring, percentiles, rebalanceo, insights, PDF
│   │   ├── entity/              # mapeo JPA
│   │   ├── repository/          # Spring Data JPA
│   │   └── dto/                 # request/response
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/        # V1 (schema), V2 (curvas públicas)
│   ├── README.md                # detalle técnico del backend
│   ├── SOURCES.md               # trazabilidad de datos públicos usados
│   └── .env.example
└── frontend/                    (o benchmark-frontend/)
    └── src/App.jsx               # formulario + dashboard de resultados
```

*(Ajustá los nombres de carpeta `backend/`/`frontend/` a como los tengas
organizados en tu repo real — el proyecto se armó como dos proyectos
separados, uno Maven y uno Vite.)*

## Cómo correr el proyecto completo

### 1. Backend
```bash
cd backend
# Setear las 3 variables de entorno (ver .env.example) con tus credenciales de Supabase
./mvnw spring-boot:run
```
Verificar en `http://localhost:8080/docs` (Swagger UI).

### 2. Frontend
```bash
cd frontend
npm install
npm run dev
```
Abrir `http://localhost:5173`. El CORS ya está habilitado en el backend
(`CorsConfig.java`) para ese puerto.

## Endpoints

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/responses` | Recibe el formulario, calcula scores, actualiza agregados |
| `GET` | `/results/{id}` | Percentiles, fricción principal, perfil cualitativo, insight de cuartil superior |
| `GET` | `/aggregates?segment=` | Dataset agregado y anónimo (global o por industria/región/tamaño) |
| `GET` | `/pdf-input/{id}` | JSON estructurado con el mismo resultado, para integraciones externas |
| `GET` | `/reports/{id}/pdf` | El reporte del operador ya renderizado como PDF descargable |

## Datos públicos usados para calibrar

La curva de referencia inicial (antes de tener volumen de datos primarios)
se calibró con el *Global Data Center Survey 2025* de Uptime Institute.
La trazabilidad completa de qué dato de esa fuente respalda cada valor
está en `backend/SOURCES.md` — clave para poder justificar cualquier
número del benchmark si alguien pregunta de dónde sale.

## Estado del proyecto

**Funcional de punta a punta:**
- ✅ Las 5 dimensiones implementadas (scoring + percentiles)
- ✅ Motor de rebalanceo dinámico público/primario
- ✅ Output personalizado (percentil, fricción principal, insight de cuartil superior)
- ✅ Base de datos anónima en Supabase, con dataset agregado
- ✅ Endpoint JSON + generación de PDF real
- ✅ Frontend conectado al backend real (sin datos mockeados)

**Pendiente / próximos pasos** (ver también `backend/README.md`):
- Calibración estadística formal de la curva pública (hoy es una
  estimación razonada anclada a una sola fuente)
- Revisión del rubric de scoring por alguien con expertise de dominio
- Reservoir sampling real en `score_samples` para datasets grandes
- Autenticación/roles si el equipo crece más allá de compartir un solo
  usuario de Postgres

## Seguridad y buenas prácticas del repo

- `.gitignore` excluye `.idea/`, `target/` y `.env` — las credenciales de
  Supabase nunca deberían llegar a un commit.
- Usar `.env.example` como referencia de qué variables necesita cada
  desarrollador, pidiendo los valores reales por un canal seguro
  (gestor de contraseñas compartido), no por chat.
- Si alguna credencial se expone accidentalmente (chat, commit, captura
  de pantalla), rotarla de inmediato desde Supabase → Project Settings → Database.
