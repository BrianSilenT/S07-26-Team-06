-- =========================================================
-- Curva de referencia PUBLICA del motor de rebalanceo.
-- Antes vivia hardcodeada en PublicReferenceCurves.java; ahora
-- vive en la DB para que se pueda recalibrar (ej. desde el
-- Table Editor de Supabase) sin tener que redeployar el backend.
--
-- Cada fila = el valor (0-100) de una dimension en un percentil
-- especifico (0,10,20,...,100). Se interpola linealmente entre
-- puntos consecutivos -- ver PublicReferenceCurves.java.
--
-- Trazabilidad completa de cada valor: ver SOURCES.md en la raiz
-- del repo. Fuente principal: Uptime Institute, "Global Data
-- Center Survey 2025".
-- =========================================================

create table public_reference_curves (
    dimension   varchar(40)  not null,   -- 'visibility' | 'coordination_latency' | 'self_quantification' | 'composite'
    percentile  smallint     not null,   -- 0,10,20,...,100
    value       smallint     not null,   -- valor de la dimension (0-100) en ese percentil
    source      varchar(240),           -- de donde sale este numero, para trazabilidad
    updated_at  timestamptz  not null default now(),

    primary key (dimension, percentile),
    constraint chk_percentile_step check (percentile in (0,10,20,30,40,50,60,70,80,90,100)),
    constraint chk_value_range check (value between 0 and 100)
);

comment on table public_reference_curves is
    'Curva de referencia publica (ancla del motor de rebalanceo). Editable sin redeploy -- ver SOURCES.md para la trazabilidad de cada valor antes de cambiarlo.';

-- VISIBILITY -- ancla: Uptime Institute GDCS 2025, solo 74% trackea PUE
-- y 84% energia; el resto de metricas de sustentabilidad <50%.
insert into public_reference_curves (dimension, percentile, value, source) values
('visibility',   0,   0, 'Limite teorico de la escala'),
('visibility',  10,   4, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('visibility',  20,   8, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('visibility',  30,  13, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('visibility',  40,  19, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('visibility',  50,  27, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('visibility',  60,  37, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('visibility',  70,  50, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('visibility',  80,  64, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('visibility',  90,  80, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('visibility', 100, 100, 'Limite teorico de la escala');

-- COORDINATION_LATENCY -- ancla: solo 35% permitiria que una IA ajuste
-- setpoints de cooling automaticamente.
insert into public_reference_curves (dimension, percentile, value, source) values
('coordination_latency',   0,   0, 'Limite teorico de la escala'),
('coordination_latency',  10,   8, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('coordination_latency',  20,  15, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('coordination_latency',  30,  21, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('coordination_latency',  40,  28, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('coordination_latency',  50,  35, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('coordination_latency',  60,  45, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('coordination_latency',  70,  58, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('coordination_latency',  80,  72, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('coordination_latency',  90,  87, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('coordination_latency', 100, 100, 'Limite teorico de la escala');

-- SELF_QUANTIFICATION -- ancla: preocupacion por "forecasting future
-- capacity requirements" subio 9pp desde 2023.
insert into public_reference_curves (dimension, percentile, value, source) values
('self_quantification',   0,   0, 'Limite teorico de la escala'),
('self_quantification',  10,   3, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('self_quantification',  20,   6, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('self_quantification',  30,   9, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('self_quantification',  40,  14, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('self_quantification',  50,  20, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('self_quantification',  60,  30, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('self_quantification',  70,  43, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('self_quantification',  80,  58, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('self_quantification',  90,  78, 'Uptime Institute GDCS 2025 -- ver SOURCES.md'),
('self_quantification', 100, 100, 'Limite teorico de la escala');

-- COMPOSITE -- promedio ponderado derivado de las 3 curvas anteriores
-- (mismos pesos que ScoringService: 0.34 / 0.33 / 0.33).
insert into public_reference_curves (dimension, percentile, value, source) values
('composite',   0,   0, 'Limite teorico de la escala'),
('composite',  10,   5, 'Derivado -- promedio ponderado de visibility/latency/self_quantification'),
('composite',  20,  10, 'Derivado -- promedio ponderado de visibility/latency/self_quantification'),
('composite',  30,  15, 'Derivado -- promedio ponderado de visibility/latency/self_quantification'),
('composite',  40,  21, 'Derivado -- promedio ponderado de visibility/latency/self_quantification'),
('composite',  50,  28, 'Derivado -- promedio ponderado de visibility/latency/self_quantification'),
('composite',  60,  38, 'Derivado -- promedio ponderado de visibility/latency/self_quantification'),
('composite',  70,  51, 'Derivado -- promedio ponderado de visibility/latency/self_quantification'),
('composite',  80,  65, 'Derivado -- promedio ponderado de visibility/latency/self_quantification'),
('composite',  90,  82, 'Derivado -- promedio ponderado de visibility/latency/self_quantification'),
('composite', 100, 100, 'Limite teorico de la escala');
