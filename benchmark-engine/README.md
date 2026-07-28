# Motor del Benchmark — Backend (Spring Boot)

Motor que procesa las respuestas del diagnóstico, calcula la posición
del operador, genera el output personalizado y alimenta el dataset
agregado. Implementa las 5 dimensiones del benchmark y el rebalanceo
dinámico público/primario.

## Stack

- Java 21 / Spring Boot 3.3
- Postgres (Supabase) + Flyway para migraciones
- springdoc-openapi → Swagger UI en `/docs`

## Setup local

```bash
export SUPABASE_DB_URL="jdbc:postgresql://<host>:5432/postgres"
export SUPABASE_DB_USER="postgres"
export SUPABASE_DB_PASSWORD="<password>"
./mvnw spring-boot:run
```

Flyway corre `V1__init_schema.sql` automáticamente al arrancar y crea
`responses`, `scores`, `aggregates` y `score_samples` en Supabase.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/responses` | Recibe el formulario, calcula scores, actualiza agregados |
| `GET` | `/results/{id}` | Output personalizado: percentiles, perfil cualitativo, insight de cuartil superior |
| `GET` | `/aggregates?segment=` | Dataset agregado y anónimo. `segment` opcional (`global`, `industry:HYPERSCALE`, `region:LATAM`, `size:MW_1_5`) |
| `GET` | `/pdf-input/{id}` | JSON de entrada para el generador de PDF (Proyecto 5) |

Documentación interactiva en `/docs` (Swagger UI) una vez levantado.

## Las 5 dimensiones y cómo se representan

| Dimensión del brief | Campo en el request | Tipo de score |
|---|---|---|
| Visibilidad cross-layer | `visibility` (vista unificada, frecuencia de actualización, tools integradas) | Numérico 0-100 |
| Atribución de fricción | `frictionAttribution` | Categórico (no tiene percentil propio, se agrega como distribución) |
| Latencia de coordinación | `coordinationLatency` | Numérico 0-100 (mapeo directo de bucket de tiempo) |
| Auto-cuantificación | `selfQuantification` (sabe %, cuándo lo midió) | Numérico 0-100 |
| Bloqueantes | `primaryBlocker` | Categórico (igual que fricción) |

`composite` es un cuarto valor numérico: promedio ponderado de las 3
dimensiones numéricas, usado como percentil general del operador.

El rubric completo de scoring está documentado en
`ScoringService.java` — es el componente pensado para iterarse más
seguido, por eso el payload crudo se guarda en `responses.raw_answers`
y permite re-scorear el histórico sin volver a preguntar si el rubric
cambia.

## Motor de rebalanceo dinámico (público ↔ primario)

**Problema que resuelve:** el día 1 no hay datos primarios, así que el
benchmark se calibra 100% contra una distribución de referencia
pública (`PublicReferenceCurves`, actualmente un *placeholder*
razonado — ver TODO en ese archivo, reemplazar con calibración real
en Fase 1). A medida que se acumulan respuestas primarias, el peso de
esos datos debe crecer, pero de forma **suave y con techo**, no como
un ratio fijo ni un salto abrupto en algún umbral.

**Fórmula** (`RebalancingService.primaryWeight`):

```
peso_primario(N) = min( N / (N + k), max_primary_weight )
```

- `N` = respuestas primarias acumuladas **en el segmento y dimensión
  evaluados** (no un contador global único — ver siguiente sección).
- `k` = *smoothing factor*. Es el valor de `N` en el que el peso
  primario llega exactamente a 50%. Configurable vía
  `benchmark.rebalancing.smoothing-factor-k` (default `50`).
- `max_primary_weight` = techo duro (default `0.95`) para nunca
  depender 100% de datos primarios, como salvaguarda ante sesgo de
  auto-selección (quién decide completar el benchmark no es una
  muestra aleatoria de la industria).

**Por qué esta forma funcional y no un ratio fijo:**
- Es monótonamente creciente en `N`: más datos primarios nunca reducen
  su propia influencia.
- Es suave: una sola respuesta nueva no puede mover el output de otro
  operador de forma abrupta (no hay "salto" al cruzar un umbral).
- Es interpretable: en `N = k`, el peso es exactamente 50/50.

**Cómo se aplica el blend — a nivel de percentil, no de distribución
cruda** (`PercentileService` + `RebalancingService.blendPercentiles`):

1. Se calcula el percentil del valor del operador contra la curva
   pública (interpolación lineal sobre `PublicReferenceCurves`).
2. Se calcula el percentil empírico contra las respuestas primarias
   guardadas en `score_samples` para ese segmento.
3. Se mezclan: `percentil_final = (1-w)*percentil_publico + w*percentil_primario`,
   con `w = peso_primario(N)`.

Blendear a nivel de percentil (y no fusionar las distribuciones
crudas) permite usar cualquier fuente pública como referencia sin
tener que reconciliar su unidad o formato con los datos primarios.

**Rebalanceo por segmento, no global:** `N` se cuenta por
`(dimensión, segmento)`, no como un contador único del benchmark.
Esto evita que, por ejemplo, 500 respuestas de operadores hyperscale
en NA le den falsa confianza estadística al percentil de un operador
edge en LATAM que es el primero de su segmento. El `segmentKey` actual
soporta `global`, `industry:*`, `region:*`, `size:*`; el resultado
individual (`/results/{id}`) usa `global` en esta versión — extender a
segmentado real es cambiar un string en `BenchmarkService.getResults`.

## Anonimato del dataset agregado

`responses` no guarda ningún identificador personal — solo metadata
de segmento *coarse* (bucket de tamaño, vertical, región) pensada
para no ser re-identificable en combinación. `/aggregates` nunca
expone filas individuales, solo `mean`, `stdDev`, curva de percentiles
y distribución de categóricas por segmento.

## Pendiente para Fase 1 (fuera de este entregable)

- Calibrar `PublicReferenceCurves` con fuentes públicas reales.
- Definir si el rubric de `ScoringService` necesita revisión por
  alguien con expertise de dominio (los pesos actuales son un punto
  de partida razonado, no una calibración validada).
- Reservoir sampling real en `score_samples` una vez que un segmento
  supere `MAX_SAMPLES_PER_SEGMENT` (hoy simplemente deja de agregar
  muestras nuevas, lo cual sesga hacia las primeras respuestas).
