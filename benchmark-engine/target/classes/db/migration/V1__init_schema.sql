-- =========================================================
-- Esquema inicial del Benchmark de Madurez de Coordinacion
-- =========================================================

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------
-- responses: una fila por diagnostico completado.
-- Solo metadata de segmento (no PII) + respuestas crudas
-- para poder re-procesar si cambia el rubric de scoring.
-- ---------------------------------------------------------
create table responses (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),

    -- metadata de segmento, usada para percentiles segmentados
    -- y para el rebalanceo dinamico. Deliberadamente coarse
    -- para evitar re-identificacion (k-anonymity minima).
    facility_size_bucket varchar(20)  not null,  -- '<1MW','1-5MW','5-20MW','20-50MW','50MW+'
    industry_vertical    varchar(40)  not null,  -- 'colocation','hyperscale','enterprise','edge','other'
    region                varchar(30) not null,  -- 'na','latam','emea','apac','other'

    -- payload crudo del formulario (ver DimensionAnswerDto),
    -- permite re-scorear si el rubric cambia sin re-preguntar.
    raw_answers         jsonb        not null,

    schema_version       smallint    not null default 1
);

create index idx_responses_segment
    on responses (facility_size_bucket, industry_vertical, region);

-- ---------------------------------------------------------
-- scores: 1:1 con responses. Resultado de aplicar el rubric.
-- ---------------------------------------------------------
create table scores (
    id                          uuid primary key default gen_random_uuid(),
    response_id                 uuid not null unique references responses(id) on delete cascade,

    visibility_score             numeric(5,2) not null,   -- 0-100
    friction_attribution         varchar(60)  not null,   -- interfaz con mas friccion percibida
    coordination_latency_score   numeric(5,2) not null,   -- 0-100 (mas alto = mas rapido)
    self_quantification_score    numeric(5,2) not null,   -- 0-100
    primary_blocker              varchar(60)  not null,   -- bloqueante principal

    composite_score               numeric(5,2) not null,  -- promedio ponderado de las 4 dims numericas

    created_at                   timestamptz not null default now()
);

create index idx_scores_composite on scores (composite_score);
create index idx_scores_visibility on scores (visibility_score);
create index idx_scores_latency on scores (coordination_latency_score);
create index idx_scores_selfquant on scores (self_quantification_score);

-- ---------------------------------------------------------
-- aggregates: rollup incremental por dimension + segmento.
-- Se actualiza en cada nueva respuesta (no es una tabla de
-- eventos individuales) para servir /aggregates y el motor
-- de rebalanceo sin tener que escanear scores en cada request.
-- ---------------------------------------------------------
create table aggregates (
    id                  uuid primary key default gen_random_uuid(),
    dimension           varchar(40) not null,   -- 'visibility' | 'coordination_latency' | 'self_quantification' | 'composite'
    segment_key         varchar(80) not null,   -- 'global' o 'industry:hyperscale' etc.

    sample_count        integer      not null default 0,
    sum_value           numeric(12,2) not null default 0,
    sum_sq_value         numeric(14,2) not null default 0,

    -- categoricas mas frecuentes, para insights ("top friction interface")
    category_counts     jsonb        not null default '{}'::jsonb,

    updated_at          timestamptz  not null default now(),

    unique (dimension, segment_key)
);

-- ---------------------------------------------------------
-- score_samples: mantiene una muestra acotada de valores por
-- dimension+segmento para poder calcular percentiles reales
-- (no solo media/desvio) sin escanear toda la tabla scores.
-- Se cappea en la capa de aplicacion (ver ScoreSampleRepository).
-- ---------------------------------------------------------
create table score_samples (
    id            bigserial primary key,
    dimension     varchar(40) not null,
    segment_key   varchar(80) not null,
    value         numeric(5,2) not null,
    created_at    timestamptz not null default now()
);

create index idx_score_samples_lookup on score_samples (dimension, segment_key);
