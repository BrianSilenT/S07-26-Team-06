import React, { useState, useEffect, useRef } from "react";
import {
  Zap, Thermometer, Server, Layers, Check, ArrowRight, ArrowLeft,
  DollarSign, Users, Wrench, ThumbsUp, Compass, Gauge, Database,
  Sparkles, ChevronRight, Info, AlertTriangle, RotateCcw, Loader2, WifiOff,
} from "lucide-react";

/* ============================================================
   INTEGRACIÓN CON EL BACKEND REAL (Spring Boot)
   Reemplaza al motor mock que corría en el cliente. Todo el
   scoring, percentiles, rebalanceo e insights ahora los calcula
   el backend — acá solo armamos el payload, llamamos a los 4
   endpoints y normalizamos la respuesta para la UI.

   Cambiar API_BASE_URL cuando se despliegue en otro lado.
   ============================================================ */

const API_BASE_URL = "http://localhost:8080";

function buildSubmissionPayload(answers) {
  return {
    facilitySizeBucket: answers.segment.size,
    industryVertical: answers.segment.industry,
    region: answers.segment.region,
    visibility: {
      hasUnifiedView: answers.visibility.hasUnifiedView,
      dataUpdateFrequency: answers.visibility.frequency,
      toolsIntegratedCount: answers.visibility.toolsCount,
    },
    frictionAttribution: answers.friction,
    coordinationLatency: answers.latency,
    selfQuantification: {
      knowsStrandedCapacityPct: answers.selfQuant.knows,
      estimatedStrandedCapacityPct: answers.selfQuant.knows ? answers.selfQuant.pct : null,
      daysSinceLastMeasurement: answers.selfQuant.knows ? answers.selfQuant.days : null,
    },
    primaryBlocker: answers.blocker,
  };
}

async function apiFetch(path, options) {
  let res;
  try {
    res = await fetch(`${API_BASE_URL}${path}`, options);
  } catch (networkErr) {
    throw new Error(
      `No se pudo conectar al backend en ${API_BASE_URL}. ¿Está corriendo (Run en IntelliJ) y con CORS habilitado?`
    );
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message || `El backend respondió con error (HTTP ${res.status}).`);
  }
  return res.json();
}

/** Convierte counts crudos de /aggregates a porcentajes sobre el total del segmento. */
function toDistributionPct(rawCounts, options, sampleCount) {
  return Object.fromEntries(
    options.map((o) => {
      const count = rawCounts?.[o.value] ?? 0;
      const pct = sampleCount > 0 ? Math.round((count / sampleCount) * 100) : 0;
      return [o.value, pct];
    })
  );
}

/** Orquesta POST /responses -> GET /results/{id} + GET /aggregates, y normaliza para la UI. */
async function submitBenchmark(answers) {
  const payload = buildSubmissionPayload(answers);
  const { responseId } = await apiFetch("/responses", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  const [results, aggregates] = await Promise.all([
    apiFetch(`/results/${responseId}`),
    apiFetch("/aggregates"),
  ]);

  const percentiles = {
    visibility: results.percentiles.visibility,
    coordination_latency: results.percentiles.coordinationLatency,
    self_quantification: results.percentiles.selfQuantification,
    composite: results.percentiles.composite,
  };

  const frictionLabel = FRICTION_OPTIONS.find((o) => o.value === results.frictionAttribution)?.label ?? results.frictionAttribution;
  const blockerLabel = BLOCKER_OPTIONS.find((o) => o.value === results.primaryBlocker)?.label ?? results.primaryBlocker;

  return {
    responseId,
    percentiles,
    weakest: results.weakestDimension,
    frictionLabel,
    blockerLabel,
    frictionValue: results.frictionAttribution,
    blockerValue: results.primaryBlocker,
    qualitativeProfile: results.qualitativeProfile,
    topQuartileInsight: results.topQuartileInsight,
    rebalancing: {
      n: results.rebalancingMetadata.primarySampleSize,
      k: aggregates.rebalancingState?.smoothingFactorK ?? "—",
      weight: results.rebalancingMetadata.primaryWeight,
    },
    frictionDistribution: toDistributionPct(aggregates.frictionAttributionDistribution, FRICTION_OPTIONS, aggregates.sampleCount),
    blockerDistribution: toDistributionPct(aggregates.primaryBlockerDistribution, BLOCKER_OPTIONS, aggregates.sampleCount),
  };
}

/* ============================================================
   OPCIONES DEL FORMULARIO (mismos valores que el DTO del backend)
   ============================================================ */

const FACILITY_SIZE_OPTIONS = [
  { value: "UNDER_1MW", label: "< 1 MW" }, { value: "MW_1_5", label: "1–5 MW" },
  { value: "MW_5_20", label: "5–20 MW" }, { value: "MW_20_50", label: "20–50 MW" }, { value: "OVER_50MW", label: "50+ MW" },
];
const INDUSTRY_OPTIONS = [
  { value: "COLOCATION", label: "Colocation" }, { value: "HYPERSCALE", label: "Hyperscale" },
  { value: "ENTERPRISE", label: "Enterprise / on-prem" }, { value: "EDGE", label: "Edge" }, { value: "OTHER", label: "Otro" },
];
const REGION_OPTIONS = [
  { value: "NA", label: "Norteamérica" }, { value: "LATAM", label: "Latam" },
  { value: "EMEA", label: "EMEA" }, { value: "APAC", label: "APAC" }, { value: "OTHER", label: "Otra" },
];
const FREQ_OPTIONS = [
  { value: "REAL_TIME", label: "Tiempo real" }, { value: "HOURLY", label: "Cada hora" }, { value: "DAILY", label: "Diaria" },
  { value: "WEEKLY_OR_LESS", label: "Semanal o menos" }, { value: "MANUAL_ONLY", label: "Solo manual / ad-hoc" },
];
const TOOLS = [
  { key: "energy", label: "Energía" }, { key: "cooling", label: "Cooling" },
  { key: "workloads", label: "Workloads" }, { key: "capacity", label: "Capacity planning" },
];
const FRICTION_OPTIONS = [
  { value: "ENERGY_COOLING", label: "Energía ↔ Cooling", icon: Zap },
  { value: "COOLING_WORKLOAD", label: "Cooling ↔ Workloads", icon: Thermometer },
  { value: "ENERGY_WORKLOAD", label: "Energía ↔ Workloads", icon: Server },
  { value: "CAPACITY_PLANNING_OPS", label: "Capacity planning ↔ Operación", icon: Layers },
  { value: "NONE_PERCEIVED", label: "No percibimos fricción", icon: Check },
];
const LATENCY_OPTIONS = [
  { value: "MINUTES", label: "Minutos", hint: "Ajuste automático" },
  { value: "UNDER_1_HOUR", label: "Menos de 1 hora", hint: "" },
  { value: "HOURS", label: "Varias horas", hint: "" },
  { value: "DAYS", label: "Días", hint: "" },
  { value: "WEEKS_OR_MANUAL_TICKET", label: "Semanas / ticket manual", hint: "" },
];
const RECENCY_OPTIONS = [
  { value: 30, label: "Último mes" }, { value: 75, label: "Último trimestre" },
  { value: 200, label: "Últimos 6–12 meses" }, { value: 500, label: "Hace más de un año" },
];
const BLOCKER_OPTIONS = [
  { value: "BUDGET", label: "Presupuesto", icon: DollarSign },
  { value: "ORG_SILOS", label: "Silos organizacionales", icon: Users },
  { value: "TOOLING_GAP", label: "Falta de herramientas", icon: Wrench },
  { value: "LACK_OF_EXEC_BUYIN", label: "Falta de buy-in ejecutivo", icon: ThumbsUp },
  { value: "DONT_KNOW_WHERE_TO_START", label: "No sabemos por dónde empezar", icon: Compass },
  { value: "NONE", label: "Ninguno", icon: Check },
];

const STEPS = [
  { key: "segment", label: "Tu facility", eyebrow: "Contexto" },
  { key: "visibility", label: "Visibilidad", eyebrow: "Dimensión 1" },
  { key: "friction", label: "Fricción", eyebrow: "Dimensión 2" },
  { key: "latency", label: "Latencia", eyebrow: "Dimensión 3" },
  { key: "selfquant", label: "Auto-cuantificación", eyebrow: "Dimensión 4" },
  { key: "blockers", label: "Bloqueantes", eyebrow: "Dimensión 5" },
];

/* ============================================================
   COMPONENTE
   ============================================================ */

export default function BenchmarkDemo() {
  const [step, setStep] = useState(-1); // -1 = hero, 0..5 = steps, 6 = resultados
  const formRef = useRef(null);
  const [answers, setAnswers] = useState({
    segment: { size: "", industry: "", region: "" },
    visibility: { hasUnifiedView: null, frequency: "", toolsCount: 0, tools: {} },
    friction: "",
    latency: "",
    selfQuant: { knows: null, pct: 15, days: null },
    blocker: "",
  });

  const [resultData, setResultData] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  const [heroStats, setHeroStats] = useState(null); // { n, weight } — null mientras carga o si falla
  const [heroOffline, setHeroOffline] = useState(false);

  useEffect(() => {
    apiFetch("/aggregates")
      .then((data) => setHeroStats({ n: data.sampleCount, weight: data.rebalancingState?.currentPrimaryWeight ?? 0 }))
      .catch(() => setHeroOffline(true));
  }, []);

  const scrollToForm = () => {
    setStep(0);
    requestAnimationFrame(() => formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
  };

  const canAdvance = () => {
    switch (STEPS[step]?.key) {
      case "segment": return !!(answers.segment.size && answers.segment.industry && answers.segment.region);
      case "visibility": return answers.visibility.hasUnifiedView !== null && !!answers.visibility.frequency;
      case "friction": return !!answers.friction;
      case "latency": return !!answers.latency;
      case "selfquant": return answers.selfQuant.knows !== null && (answers.selfQuant.knows === false || answers.selfQuant.days !== null);
      case "blockers": return !!answers.blocker;
      default: return true;
    }
  };

  const next = async () => {
    if (step !== STEPS.length - 1) { setStep((s) => s + 1); return; }

    setSubmitting(true);
    setSubmitError(null);
    try {
      const data = await submitBenchmark(answers);
      setResultData(data);
      setStep(6);
      requestAnimationFrame(() => formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
    } catch (err) {
      setSubmitError(err.message || "Ocurrió un error inesperado al enviar el diagnóstico.");
    } finally {
      setSubmitting(false);
    }
  };
  const back = () => setStep((s) => Math.max(0, s - 1));
  const restart = () => {
    setAnswers({
      segment: { size: "", industry: "", region: "" },
      visibility: { hasUnifiedView: null, frequency: "", toolsCount: 0, tools: {} },
      friction: "", latency: "",
      selfQuant: { knows: null, pct: 15, days: null },
      blocker: "",
    });
    setResultData(null);
    setSubmitError(null);
    setStep(-1);
  };

  return (
    <div className="bmk-app">
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@500;600;700&family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@400;500;600&display=swap');

        .bmk-app {
          --bg:#0A0F1C; --panel:#121A2C; --panel-raised:#19233A; --panel-hi:#202B45;
          --border:#28324B; --border-soft:#1D2740;
          --text:#E9ECF3; --text-muted:#8D95AC; --text-faint:#5C6480;
          --teal:#42C2A8; --teal-soft:rgba(66,194,168,0.14); --teal-ink:#04231D;
          --amber:#E3A75C; --amber-soft:rgba(227,167,92,0.14);
          --red:#E1694E; --red-soft:rgba(225,105,78,0.14);
          --blue:#6F90F0; --blue-soft:rgba(111,144,240,0.14);
          background:var(--bg); color:var(--text);
          font-family:'Inter',system-ui,sans-serif;
          min-height:100vh;
        }
        .bmk-app * { box-sizing:border-box; }
        .bmk-display { font-family:'Space Grotesk',system-ui,sans-serif; }
        .bmk-mono { font-family:'JetBrains Mono',ui-monospace,monospace; }

        .bmk-eyebrow { font-family:'JetBrains Mono',monospace; letter-spacing:.14em; text-transform:uppercase; font-size:.7rem; color:var(--teal); font-weight:500; }
        .bmk-shell { max-width:1080px; margin:0 auto; padding:0 24px; }

        .bmk-hero { padding:88px 0 64px; border-bottom:1px solid var(--border-soft); position:relative; overflow:hidden; }
        .bmk-hero::before{ content:''; position:absolute; top:-140px; right:-140px; width:420px; height:420px; border-radius:50%;
          background:radial-gradient(circle, rgba(66,194,168,0.16), transparent 70%); pointer-events:none; }
        .bmk-hero-headline { font-size:clamp(2.1rem,4.4vw,3.4rem); font-weight:700; letter-spacing:-0.02em; line-height:1.06; margin:18px 0 20px; max-width:820px; }
        .bmk-hero-sub { color:var(--text-muted); font-size:1.05rem; line-height:1.6; max-width:640px; margin-bottom:40px; }

        .bmk-leak-card { background:var(--panel); border:1px solid var(--border); border-radius:18px; padding:28px 28px 24px; max-width:560px; }
        .bmk-leak-top { display:flex; justify-content:space-between; align-items:baseline; margin-bottom:14px; }
        .bmk-leak-bar { height:16px; border-radius:999px; overflow:hidden; display:flex; background:var(--panel-raised); border:1px solid var(--border-soft); }
        .bmk-leak-delivered { background:linear-gradient(90deg,var(--teal),#2FA891); transition:width 1s cubic-bezier(.16,1,.3,1); }
        .bmk-leak-stranded { background:linear-gradient(90deg,#E3A75C,#D9924A); transition:width 1s cubic-bezier(.16,1,.3,1); }
        .bmk-leak-legend { display:flex; gap:22px; margin-top:14px; font-size:.82rem; color:var(--text-muted); }
        .bmk-dot { width:8px; height:8px; border-radius:999px; display:inline-block; margin-right:7px; }

        .bmk-btn-primary { background:var(--teal); color:var(--teal-ink); font-weight:600; border-radius:11px; padding:.85rem 1.5rem;
          display:inline-flex; align-items:center; gap:8px; transition:transform .15s ease, box-shadow .15s ease; border:none; cursor:pointer; font-size:.95rem; }
        .bmk-btn-primary:hover { transform:translateY(-1px); box-shadow:0 10px 28px rgba(66,194,168,.28); }
        .bmk-btn-primary:disabled { opacity:.35; cursor:not-allowed; transform:none; box-shadow:none; }
        .bmk-btn-secondary { background:transparent; border:1px solid var(--border); color:var(--text-muted); border-radius:11px; padding:.85rem 1.4rem;
          display:inline-flex; align-items:center; gap:8px; cursor:pointer; font-size:.95rem; transition:border-color .15s ease, color .15s ease; }
        .bmk-btn-secondary:hover { border-color:var(--text-faint); color:var(--text); }

        .bmk-stats-row { display:flex; gap:36px; margin-top:36px; flex-wrap:wrap; }
        .bmk-stat-num { font-family:'Space Grotesk',sans-serif; font-size:1.5rem; font-weight:700; }
        .bmk-stat-label { font-size:.76rem; color:var(--text-faint); margin-top:2px; }

        .bmk-formwrap { padding:64px 0 100px; }
        .bmk-rail { display:flex; align-items:center; gap:0; margin-bottom:44px; overflow-x:auto; padding-bottom:4px; }
        .bmk-rail-item { display:flex; align-items:center; gap:8px; white-space:nowrap; }
        .bmk-rail-dot { width:9px; height:9px; border-radius:999px; background:var(--border); flex-shrink:0; transition:background .2s ease; }
        .bmk-rail-dot.active { background:var(--teal); box-shadow:0 0 0 4px var(--teal-soft); }
        .bmk-rail-dot.done { background:var(--blue); }
        .bmk-rail-label { font-size:.78rem; color:var(--text-faint); }
        .bmk-rail-label.active { color:var(--text); font-weight:500; }
        .bmk-rail-line { width:28px; height:1px; background:var(--border); margin:0 10px; flex-shrink:0; }

        .bmk-panel { background:var(--panel); border:1px solid var(--border); border-radius:20px; padding:40px; }
        .bmk-step-title { font-size:1.5rem; font-weight:700; margin:6px 0 8px; }
        .bmk-step-sub { color:var(--text-muted); font-size:.94rem; margin-bottom:30px; line-height:1.55; }

        .bmk-field-label { font-size:.84rem; color:var(--text-muted); margin-bottom:10px; display:block; font-weight:500; }
        .bmk-select { width:100%; background:var(--panel-raised); border:1px solid var(--border); color:var(--text); border-radius:10px;
          padding:.7rem .9rem; font-size:.92rem; font-family:inherit; appearance:none; cursor:pointer; }
        .bmk-select:focus { outline:2px solid var(--teal); outline-offset:1px; }

        .bmk-grid3 { display:grid; grid-template-columns:repeat(3,1fr); gap:14px; }
        .bmk-grid2 { display:grid; grid-template-columns:repeat(2,1fr); gap:14px; }

        .bmk-card-opt { border:1px solid var(--border); background:var(--panel-raised); border-radius:13px; padding:14px 16px; cursor:pointer;
          transition:border-color .15s ease, background .15s ease; display:flex; align-items:center; gap:10px; }
        .bmk-card-opt:hover { border-color:var(--border-soft); background:var(--panel-hi); }
        .bmk-card-opt.selected { border-color:var(--teal); background:var(--teal-soft); }
        .bmk-card-opt-label { font-size:.9rem; }
        .bmk-card-opt-hint { font-size:.74rem; color:var(--text-faint); margin-top:2px; }

        .bmk-toggle-row { display:flex; gap:10px; }
        .bmk-toggle { flex:1; text-align:center; padding:.75rem; border-radius:10px; border:1px solid var(--border); background:var(--panel-raised);
          cursor:pointer; font-size:.9rem; transition:all .15s ease; }
        .bmk-toggle.selected { border-color:var(--teal); background:var(--teal-soft); color:var(--teal); font-weight:600; }

        .bmk-checkbox-row { display:flex; align-items:center; gap:10px; padding:11px 14px; border:1px solid var(--border); border-radius:10px;
          background:var(--panel-raised); cursor:pointer; margin-bottom:8px; transition:border-color .15s ease; }
        .bmk-checkbox-row:hover { border-color:var(--border-soft); }
        .bmk-checkbox-box { width:16px; height:16px; border-radius:4px; border:1px solid var(--text-faint); display:flex; align-items:center; justify-content:center; flex-shrink:0; }
        .bmk-checkbox-box.on { background:var(--teal); border-color:var(--teal); }

        .bmk-nav-row { display:flex; justify-content:space-between; align-items:center; margin-top:36px; }

        .bmk-slider { width:100%; accent-color:#42C2A8; }

        /* ---- resultados ---- */
        .bmk-results-head { display:flex; align-items:baseline; justify-content:space-between; flex-wrap:wrap; gap:16px; margin-bottom:36px; }
        .bmk-composite-num { font-family:'Space Grotesk',sans-serif; font-size:3.2rem; font-weight:700; line-height:1; }

        .bmk-dim-row { padding:18px 0; border-top:1px solid var(--border-soft); }
        .bmk-dim-row:first-child { border-top:none; }
        .bmk-dim-top { display:flex; justify-content:space-between; align-items:center; margin-bottom:10px; }
        .bmk-dim-name { font-size:.9rem; font-weight:500; display:flex; align-items:center; gap:8px; }
        .bmk-dim-pct { font-family:'JetBrains Mono',monospace; font-size:.92rem; }
        .bmk-track { position:relative; height:8px; border-radius:999px; background:var(--panel-raised); border:1px solid var(--border-soft); }
        .bmk-fill { position:absolute; left:0; top:0; bottom:0; border-radius:999px; background:linear-gradient(90deg,var(--blue),var(--teal)); transition:width .8s cubic-bezier(.16,1,.3,1); }
        .bmk-marker { position:absolute; top:-3px; width:2px; height:14px; background:var(--text-faint); }

        .bmk-callout { border:1px solid var(--border); border-radius:16px; padding:24px 26px; background:var(--panel-raised); }
        .bmk-callout.weak { border-color:rgba(227,167,92,.4); background:var(--amber-soft); }
        .bmk-callout.quartile { border-color:rgba(66,194,168,.4); background:var(--teal-soft); }
        .bmk-callout-title { font-size:.78rem; text-transform:uppercase; letter-spacing:.08em; font-family:'JetBrains Mono',monospace; margin-bottom:10px; display:flex; align-items:center; gap:8px; }
        .bmk-callout-text { font-size:.94rem; line-height:1.65; color:var(--text); }

        .bmk-split-bar { display:flex; height:12px; border-radius:999px; overflow:hidden; border:1px solid var(--border-soft); }
        .bmk-split-public { background:var(--blue); transition:width .8s ease; }
        .bmk-split-primary { background:var(--teal); transition:width .8s ease; }

        .bmk-chip-row { display:flex; flex-wrap:wrap; gap:8px; }
        .bmk-chip { border:1px solid var(--border); background:var(--panel-raised); border-radius:999px; padding:.4rem .85rem; font-size:.82rem; color:var(--text-muted); }

        .bmk-dist-row { display:flex; align-items:center; gap:12px; padding:7px 0; }
        .bmk-dist-label { width:210px; font-size:.82rem; color:var(--text-muted); flex-shrink:0; }
        .bmk-dist-track { flex:1; height:7px; border-radius:999px; background:var(--panel-raised); position:relative; overflow:hidden; }
        .bmk-dist-fill { height:100%; border-radius:999px; background:var(--border-soft); }
        .bmk-dist-fill.mine { background:var(--teal); }
        .bmk-dist-pct { width:36px; text-align:right; font-family:'JetBrains Mono',monospace; font-size:.78rem; color:var(--text-faint); flex-shrink:0; }

        .bmk-badge-mock { font-family:'JetBrains Mono',monospace; font-size:.68rem; text-transform:uppercase; letter-spacing:.08em;
          color:var(--text-faint); border:1px dashed var(--border); border-radius:999px; padding:.3rem .7rem; display:inline-flex; align-items:center; gap:6px; }
        .bmk-badge-live { font-family:'JetBrains Mono',monospace; font-size:.68rem; text-transform:uppercase; letter-spacing:.08em;
          color:var(--teal); border:1px solid rgba(66,194,168,.4); background:var(--teal-soft); border-radius:999px; padding:.3rem .7rem; display:inline-flex; align-items:center; gap:6px; }

        .bmk-spin { animation: bmk-spin 0.9s linear infinite; }
        @keyframes bmk-spin { from { transform:rotate(0deg); } to { transform:rotate(360deg); } }

        .bmk-error-box { border:1px solid rgba(225,105,78,.4); background:var(--red-soft); border-radius:12px; padding:14px 16px;
          margin-top:16px; font-size:.86rem; color:var(--text); display:flex; align-items:flex-start; gap:10px; line-height:1.5; }

        @media (max-width:720px){ .bmk-grid3{grid-template-columns:1fr;} .bmk-grid2{grid-template-columns:1fr;} .bmk-panel{padding:26px;} }
      `}</style>

      {/* ---------------- HERO ---------------- */}
      <section className="bmk-hero">
        <div className="bmk-shell">
          <span className="bmk-eyebrow">Benchmark de coordinación de infraestructura</span>
          <h1 className="bmk-hero-headline bmk-display">
            Pagás por megawatts que nunca se encienden.
          </h1>
          <p className="bmk-hero-sub">
            Un diagnóstico anónimo de menos de 10 minutos te dice, con datos reales de la industria,
            en qué capa de tu facility se pierde capacidad — y qué hacen distinto los operadores que no la pierden.
          </p>

          <div className="bmk-leak-card">
            <div className="bmk-leak-top">
              <span className="bmk-eyebrow" style={{ color: "var(--text-faint)" }}>Promedio de la industria (mock)</span>
              <span className="bmk-mono" style={{ fontSize: ".8rem", color: "var(--text-faint)" }}>capacidad pagada = 100%</span>
            </div>
            <div className="bmk-leak-bar">
              <div className="bmk-leak-delivered" style={{ width: "82%" }} />
              <div className="bmk-leak-stranded" style={{ width: "18%" }} />
            </div>
            <div className="bmk-leak-legend">
              <span><span className="bmk-dot" style={{ background: "var(--teal)" }} />82% entregada</span>
              <span><span className="bmk-dot" style={{ background: "var(--amber)" }} />18% atrapada (stranded)</span>
            </div>
          </div>

          <div className="bmk-stats-row">
            <button className="bmk-btn-primary" onClick={scrollToForm}>
              Empezar diagnóstico <ArrowRight size={16} />
            </button>
            {heroOffline ? (
              <div style={{ display: "flex", alignItems: "center", gap: 8, color: "var(--text-faint)", fontSize: ".82rem" }}>
                <WifiOff size={15} /> No se pudo conectar al backend ({API_BASE_URL}). ¿Está corriendo?
              </div>
            ) : (
              <>
                <div>
                  <div className="bmk-stat-num bmk-mono" style={{ color: "var(--teal)" }}>{heroStats ? heroStats.n : "—"}</div>
                  <div className="bmk-stat-label">respuestas primarias acumuladas</div>
                </div>
                <div>
                  <div className="bmk-stat-num bmk-mono" style={{ color: "var(--blue)" }}>{heroStats ? `${Math.round(heroStats.weight * 100)}%` : "—"}</div>
                  <div className="bmk-stat-label">peso de dato primario en tu cálculo</div>
                </div>
              </>
            )}
          </div>
        </div>
      </section>

      {/* ---------------- FORM / RESULTS ---------------- */}
      <section className="bmk-formwrap" ref={formRef}>
        <div className="bmk-shell">
          {step === -1 && (
            <div style={{ textAlign: "center", color: "var(--text-faint)", fontSize: ".9rem" }}>
              Tocá "Empezar diagnóstico" arriba para ver el formulario.
            </div>
          )}

          {step >= 0 && step < 6 && (
            <>
              <div className="bmk-rail">
                {STEPS.map((s, i) => (
                  <React.Fragment key={s.key}>
                    <div className="bmk-rail-item">
                      <div className={`bmk-rail-dot ${i === step ? "active" : i < step ? "done" : ""}`} />
                      <span className={`bmk-rail-label ${i === step ? "active" : ""}`}>{s.label}</span>
                    </div>
                    {i < STEPS.length - 1 && <div className="bmk-rail-line" />}
                  </React.Fragment>
                ))}
              </div>

              <div className="bmk-panel">
                <span className="bmk-eyebrow">{STEPS[step].eyebrow}</span>

                {STEPS[step].key === "segment" && (
                  <SegmentStep answers={answers} setAnswers={setAnswers} />
                )}
                {STEPS[step].key === "visibility" && (
                  <VisibilityStep answers={answers} setAnswers={setAnswers} />
                )}
                {STEPS[step].key === "friction" && (
                  <FrictionStep answers={answers} setAnswers={setAnswers} />
                )}
                {STEPS[step].key === "latency" && (
                  <LatencyStep answers={answers} setAnswers={setAnswers} />
                )}
                {STEPS[step].key === "selfquant" && (
                  <SelfQuantStep answers={answers} setAnswers={setAnswers} />
                )}
                {STEPS[step].key === "blockers" && (
                  <BlockersStep answers={answers} setAnswers={setAnswers} />
                )}

                <div className="bmk-nav-row">
                  <button className="bmk-btn-secondary" onClick={back} disabled={step === 0} style={{ opacity: step === 0 ? 0.35 : 1 }}>
                    <ArrowLeft size={15} /> Atrás
                  </button>
                  <button className="bmk-btn-primary" onClick={next} disabled={!canAdvance() || submitting}>
                    {submitting ? (
                      <><Loader2 size={16} className="bmk-spin" /> Enviando…</>
                    ) : (
                      <>{step === STEPS.length - 1 ? "Ver mi resultado" : "Siguiente"} <ChevronRight size={16} /></>
                    )}
                  </button>
                </div>

                {submitError && (
                  <div className="bmk-error-box">
                    <AlertTriangle size={16} style={{ flexShrink: 0, marginTop: 1 }} color="var(--red)" />
                    <span>{submitError}</span>
                  </div>
                )}
              </div>
            </>
          )}

          {step === 6 && resultData && <Results result={resultData} onRestart={restart} />}
        </div>
      </section>
    </div>
  );
}

/* ============================================================
   PASOS DEL FORMULARIO
   ============================================================ */

function SegmentStep({ answers, setAnswers }) {
  const set = (field, value) => setAnswers((a) => ({ ...a, segment: { ...a.segment, [field]: value } }));
  return (
    <div>
      <h2 className="bmk-step-title bmk-display">Contanos sobre tu facility</h2>
      <p className="bmk-step-sub">Solo para segmentar tu comparación. No pedimos nombre de empresa ni datos de contacto — el diagnóstico es anónimo.</p>
      <div className="bmk-grid3">
        <div>
          <label className="bmk-field-label">Tamaño</label>
          <select className="bmk-select" value={answers.segment.size} onChange={(e) => set("size", e.target.value)}>
            <option value="">Seleccionar…</option>
            {FACILITY_SIZE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </div>
        <div>
          <label className="bmk-field-label">Vertical</label>
          <select className="bmk-select" value={answers.segment.industry} onChange={(e) => set("industry", e.target.value)}>
            <option value="">Seleccionar…</option>
            {INDUSTRY_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </div>
        <div>
          <label className="bmk-field-label">Región</label>
          <select className="bmk-select" value={answers.segment.region} onChange={(e) => set("region", e.target.value)}>
            <option value="">Seleccionar…</option>
            {REGION_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </div>
      </div>
    </div>
  );
}

function VisibilityStep({ answers, setAnswers }) {
  const v = answers.visibility;
  const setV = (patch) => setAnswers((a) => ({ ...a, visibility: { ...a.visibility, ...patch } }));
  const toggleTool = (key) => {
    const tools = { ...v.tools, [key]: !v.tools[key] };
    const count = Object.values(tools).filter(Boolean).length;
    setV({ tools, toolsCount: count });
  };
  return (
    <div>
      <h2 className="bmk-step-title bmk-display">Visibilidad cross-layer</h2>
      <p className="bmk-step-sub">¿Tenés una vista unificada de energía, cooling y workloads?</p>

      <label className="bmk-field-label">¿Existe esa vista unificada hoy?</label>
      <div className="bmk-toggle-row" style={{ marginBottom: 24 }}>
        <div className={`bmk-toggle ${v.hasUnifiedView === true ? "selected" : ""}`} onClick={() => setV({ hasUnifiedView: true })}>Sí</div>
        <div className={`bmk-toggle ${v.hasUnifiedView === false ? "selected" : ""}`} onClick={() => setV({ hasUnifiedView: false })}>No</div>
      </div>

      <label className="bmk-field-label">¿Con qué frecuencia se actualizan esos datos?</label>
      <select className="bmk-select" style={{ marginBottom: 24 }} value={v.frequency} onChange={(e) => setV({ frequency: e.target.value })}>
        <option value="">Seleccionar…</option>
        {FREQ_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>

      <label className="bmk-field-label">¿Qué capas están integradas en esa vista? ({v.toolsCount}/4)</label>
      {TOOLS.map((t) => (
        <div key={t.key} className="bmk-checkbox-row" onClick={() => toggleTool(t.key)}>
          <div className={`bmk-checkbox-box ${v.tools[t.key] ? "on" : ""}`}>{v.tools[t.key] && <Check size={11} color="#04231D" />}</div>
          <span style={{ fontSize: ".9rem" }}>{t.label}</span>
        </div>
      ))}
    </div>
  );
}

function FrictionStep({ answers, setAnswers }) {
  return (
    <div>
      <h2 className="bmk-step-title bmk-display">Atribución de fricción</h2>
      <p className="bmk-step-sub">¿En qué interfaz entre capas percibís más pérdida de capacidad?</p>
      <div className="bmk-grid2">
        {FRICTION_OPTIONS.map((o) => {
          const Icon = o.icon;
          const selected = answers.friction === o.value;
          return (
            <div key={o.value} className={`bmk-card-opt ${selected ? "selected" : ""}`} onClick={() => setAnswers((a) => ({ ...a, friction: o.value }))}>
              <Icon size={17} color={selected ? "var(--teal)" : "var(--text-faint)"} />
              <span className="bmk-card-opt-label">{o.label}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function LatencyStep({ answers, setAnswers }) {
  return (
    <div>
      <h2 className="bmk-step-title bmk-display">Latencia de coordinación</h2>
      <p className="bmk-step-sub">Cuando cambia el workload, ¿qué tan rápido se ajustan cooling y energía?</p>
      <div className="bmk-grid2">
        {LATENCY_OPTIONS.map((o) => {
          const selected = answers.latency === o.value;
          return (
            <div key={o.value} className={`bmk-card-opt ${selected ? "selected" : ""}`} onClick={() => setAnswers((a) => ({ ...a, latency: o.value }))}>
              <div>
                <div className="bmk-card-opt-label">{o.label}</div>
                {o.hint && <div className="bmk-card-opt-hint">{o.hint}</div>}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function SelfQuantStep({ answers, setAnswers }) {
  const s = answers.selfQuant;
  const setS = (patch) => setAnswers((a) => ({ ...a, selfQuant: { ...a.selfQuant, ...patch } }));
  return (
    <div>
      <h2 className="bmk-step-title bmk-display">Auto-cuantificación</h2>
      <p className="bmk-step-sub">¿Sabés cuánta stranded capacity tenés hoy?</p>

      <label className="bmk-field-label">¿Tenés un número, aunque sea estimado?</label>
      <div className="bmk-toggle-row" style={{ marginBottom: 24 }}>
        <div className={`bmk-toggle ${s.knows === true ? "selected" : ""}`} onClick={() => setS({ knows: true })}>Sí</div>
        <div className={`bmk-toggle ${s.knows === false ? "selected" : ""}`} onClick={() => setS({ knows: false, days: null })}>No</div>
      </div>

      {s.knows && (
        <>
          <label className="bmk-field-label">Estimación de capacidad ociosa: {s.pct}%</label>
          <input type="range" min="0" max="60" value={s.pct} onChange={(e) => setS({ pct: Number(e.target.value) })} className="bmk-slider" style={{ marginBottom: 24 }} />

          <label className="bmk-field-label">¿Cuándo mediste esto por última vez?</label>
          <div className="bmk-grid2">
            {RECENCY_OPTIONS.map((o) => (
              <div key={o.value} className={`bmk-card-opt ${s.days === o.value ? "selected" : ""}`} onClick={() => setS({ days: o.value })}>
                <span className="bmk-card-opt-label">{o.label}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function BlockersStep({ answers, setAnswers }) {
  return (
    <div>
      <h2 className="bmk-step-title bmk-display">Bloqueantes</h2>
      <p className="bmk-step-sub">Si supieras exactamente dónde está el problema, ¿qué te impediría resolverlo?</p>
      <div className="bmk-grid2">
        {BLOCKER_OPTIONS.map((o) => {
          const Icon = o.icon;
          const selected = answers.blocker === o.value;
          return (
            <div key={o.value} className={`bmk-card-opt ${selected ? "selected" : ""}`} onClick={() => setAnswers((a) => ({ ...a, blocker: o.value }))}>
              <Icon size={17} color={selected ? "var(--teal)" : "var(--text-faint)"} />
              <span className="bmk-card-opt-label">{o.label}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ============================================================
   RESULTADOS
   ============================================================ */

function DimRow({ icon: Icon, name, value, marker }) {
  return (
    <div className="bmk-dim-row">
      <div className="bmk-dim-top">
        <span className="bmk-dim-name"><Icon size={15} color="var(--text-faint)" /> {name}</span>
        <span className="bmk-dim-pct bmk-mono">p{value}</span>
      </div>
      <div className="bmk-track">
        <div className="bmk-fill" style={{ width: `${value}%` }} />
        <div className="bmk-marker" style={{ left: `${marker}%` }} title="mediana de referencia (p50)" />
      </div>
    </div>
  );
}

function Results({ result, onRestart }) {
  const { percentiles, weakest, frictionLabel, blockerLabel, qualitativeProfile, topQuartileInsight, rebalancing, frictionDistribution, blockerDistribution, responseId } = result;
  const weakestLabel = { visibility: "Visibilidad", coordination_latency: "Latencia de coordinación", self_quantification: "Auto-cuantificación" }[weakest];

  return (
    <div>
      <div className="bmk-badge-live" style={{ marginBottom: 18 }}>
        <Database size={11} /> Conectado al backend real · response id {responseId?.slice(0, 8)}…
      </div>

      <div className="bmk-panel" style={{ marginBottom: 24 }}>
        <div className="bmk-results-head">
          <div>
            <span className="bmk-eyebrow">Tu posición relativa</span>
            <div className="bmk-composite-num bmk-display">Percentil {percentiles.composite}</div>
            <p style={{ color: "var(--text-muted)", fontSize: ".9rem", marginTop: 6 }}>
              Estás por delante del {percentiles.composite}% de operadores comparables en coordinación general.
            </p>
          </div>
          <div className="bmk-chip-row">
            <span className="bmk-chip">Fricción: {frictionLabel}</span>
            <span className="bmk-chip">Bloqueante: {blockerLabel}</span>
          </div>
        </div>

        <DimRow icon={Server} name="Visibilidad cross-layer" value={percentiles.visibility} marker={50} />
        <DimRow icon={Zap} name="Latencia de coordinación" value={percentiles.coordination_latency} marker={50} />
        <DimRow icon={Gauge} name="Auto-cuantificación" value={percentiles.self_quantification} marker={50} />
      </div>

      <div className="bmk-grid2" style={{ marginBottom: 24, alignItems: "stretch" }}>
        <div className="bmk-callout weak">
          <div className="bmk-callout-title" style={{ color: "var(--amber)" }}><AlertTriangle size={13} /> Punto más débil: {weakestLabel}</div>
          <p className="bmk-callout-text">{qualitativeProfile}</p>
        </div>
        <div className="bmk-callout quartile">
          <div className="bmk-callout-title" style={{ color: "var(--teal)" }}><Sparkles size={13} /> Qué hace distinto el cuartil superior</div>
          <p className="bmk-callout-text">{topQuartileInsight}</p>
        </div>
      </div>

      {/* Motor de rebalanceo — transparencia */}
      <div className="bmk-panel" style={{ marginBottom: 24 }}>
        <span className="bmk-eyebrow" style={{ display: "flex", alignItems: "center", gap: 6 }}><Info size={12} /> Motor de rebalanceo dinámico</span>
        <p style={{ color: "var(--text-muted)", fontSize: ".88rem", margin: "10px 0 16px", lineHeight: 1.6 }}>
          Tu percentil combina la curva de referencia pública con la distribución primaria real, ponderada por
          cuántas respuestas primarias hay disponibles: <span className="bmk-mono">peso = N / (N + k)</span>, con techo del 95%.
        </p>
        <div className="bmk-split-bar" style={{ marginBottom: 10 }}>
          <div className="bmk-split-public" style={{ width: `${(1 - rebalancing.weight) * 100}%` }} />
          <div className="bmk-split-primary" style={{ width: `${rebalancing.weight * 100}%` }} />
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: ".78rem", color: "var(--text-faint)" }}>
          <span><span className="bmk-dot" style={{ background: "var(--blue)" }} />Curva pública ({Math.round((1 - rebalancing.weight) * 100)}%)</span>
          <span className="bmk-mono">N={rebalancing.n} · k={rebalancing.k}</span>
          <span><span className="bmk-dot" style={{ background: "var(--teal)" }} />Dataset primario ({Math.round(rebalancing.weight * 100)}%)</span>
        </div>
      </div>

      {/* Dataset agregado real */}
      <div className="bmk-panel" style={{ marginBottom: 24 }}>
        <span className="bmk-eyebrow" style={{ display: "flex", alignItems: "center", gap: 6 }}><Database size={12} /> Dataset agregado (GET /aggregates)</span>
        <p style={{ color: "var(--text-muted)", fontSize: ".85rem", margin: "10px 0 18px" }}>Distribución real de la industria por interfaz de fricción y bloqueante principal. Tu respuesta ya está incluida, sin exponer datos individuales.</p>

        <div style={{ marginBottom: 18 }}>
          <div style={{ fontSize: ".8rem", fontWeight: 500, marginBottom: 8, color: "var(--text-muted)" }}>Fricción principal reportada</div>
          {FRICTION_OPTIONS.map((o) => (
            <div className="bmk-dist-row" key={o.value}>
              <span className="bmk-dist-label">{o.label}</span>
              <div className="bmk-dist-track"><div className={`bmk-dist-fill ${result.frictionValue === o.value ? "mine" : ""}`} style={{ width: `${frictionDistribution[o.value] ?? 0}%` }} /></div>
              <span className="bmk-dist-pct">{frictionDistribution[o.value] ?? 0}%</span>
            </div>
          ))}
        </div>

        <div>
          <div style={{ fontSize: ".8rem", fontWeight: 500, marginBottom: 8, color: "var(--text-muted)" }}>Bloqueante principal reportado</div>
          {BLOCKER_OPTIONS.map((o) => (
            <div className="bmk-dist-row" key={o.value}>
              <span className="bmk-dist-label">{o.label}</span>
              <div className="bmk-dist-track"><div className={`bmk-dist-fill ${result.blockerValue === o.value ? "mine" : ""}`} style={{ width: `${blockerDistribution[o.value] ?? 0}%` }} /></div>
              <span className="bmk-dist-pct">{blockerDistribution[o.value] ?? 0}%</span>
            </div>
          ))}
        </div>
      </div>

      <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
        <button className="bmk-btn-primary" onClick={() => window.open(`${API_BASE_URL}/reports/${responseId}/pdf`, "_blank")}>
          <Sparkles size={15} /> Descargar reporte en PDF
        </button>
        <button className="bmk-btn-secondary" onClick={() => window.open(`${API_BASE_URL}/pdf-input/${responseId}`, "_blank")}>
          Ver JSON (GET /pdf-input/:id)
        </button>
        <button className="bmk-btn-secondary" onClick={onRestart}><RotateCcw size={15} /> Volver a intentar</button>
      </div>
    </div>
  );
}