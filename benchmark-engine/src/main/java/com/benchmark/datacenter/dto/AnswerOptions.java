package com.benchmark.datacenter.dto;

/**
 * Enums de las opciones cerradas del formulario. Mantenerlas como
 * enums (en vez de free-text) es lo que permite agregarlas de forma
 * anonima y comparable entre operadores.
 */
public class AnswerOptions {

    public enum DataUpdateFrequency { REAL_TIME, HOURLY, DAILY, WEEKLY_OR_LESS, MANUAL_ONLY }

    /** Interfaz entre capas donde perciben mas perdida de capacidad. */
    public enum FrictionInterface {
        ENERGY_COOLING, COOLING_WORKLOAD, ENERGY_WORKLOAD, CAPACITY_PLANNING_OPS, NONE_PERCEIVED
    }

    /** Tiempo que tardan cooling/energia en reaccionar a un cambio de workload. */
    public enum CoordinationLatencyBucket {
        MINUTES, UNDER_1_HOUR, HOURS, DAYS, WEEKS_OR_MANUAL_TICKET
    }

    public enum Blocker {
        BUDGET, ORG_SILOS, TOOLING_GAP, LACK_OF_EXEC_BUYIN, DONT_KNOW_WHERE_TO_START, NONE
    }

    public enum FacilitySizeBucket { UNDER_1MW, MW_1_5, MW_5_20, MW_20_50, OVER_50MW }

    public enum IndustryVertical { COLOCATION, HYPERSCALE, ENTERPRISE, EDGE, OTHER }

    public enum Region { NA, LATAM, EMEA, APAC, OTHER }

    private AnswerOptions() {}
}
