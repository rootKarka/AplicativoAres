package com.tecsup.aresapp.data

// ── Bitácora ──────────────────────────────────────────────────────
data class BitacoraDto(
    val id:           Int,
    val mision:       Int,
    val usuario:      Int?,
    val tipo_entrada: String,
    val contenido:    String,
    val es_voz:       Boolean,
    val latitud:      Double,
    val longitud:     Double,
    val fecha:        String,
)

data class BitacoraRequest(
    val mision:       Int,
    val usuario:      Int,
    val tipo_entrada: String,
    val contenido:    String,
    val es_voz:       Boolean = false,
    val latitud:      Double  = 0.0,
    val longitud:     Double  = 0.0,
)

// ── Reporte Actualización ─────────────────────────────────────────
data class ReporteActualizacionRequest(
    val mision:              Int,
    val autor:               Int,
    val nivel_riesgo:        String,
    val resumen:             String,
    val victimas_heridas:    Int,
    val victimas_fallecidas: Int,
    val victimas_rescatadas: Int,
    val accion_recomendada:  String,
)

data class ReporteActualizacionDto(
    val id:           Int,
    val nivel_riesgo: String,
    val resumen:      String,
    val created_at:   String,
)

// ── Reporte Final ─────────────────────────────────────────────────
data class ReporteFinalRequest(
    val mision:                 Int,
    val generado_por:           Int,
    val victimas_heridas:       Int,
    val victimas_fallecidas:    Int,
    val victimas_rescatadas:    Int,
    val victimas_sin_confirmar: Int,
    val nivel_riesgo_maximo:    String,
    val duracion_minutos:       Int,
)

data class ReporteFinalDto(
    val id:                Int,
    val estado_generacion: String,
    val total_alertas:     Int,
    val alertas_criticas:  Int,
    val bateria_inicio:    Double,
    val bateria_fin:       Double,
)

// ── Resumen Misión ────────────────────────────────────────────────
data class ResumenMisionDto(
    val bateria_inicio:       Double,
    val latencia_promedio_ms: Int,
    val total_alertas:        Int,
    val alertas_criticas:     Int,
    val duracion_minutos:     Int,
)

// ── Evidencia ─────────────────────────────────────────────────────
data class EvidenciaDto(
    val id:  Int,
    val url: String?,
)

// ── Misión Cerrada ────────────────────────────────────────────────
data class MisionCerradaDto(
    val status:    String,
    val estado:    String,
    val fecha_fin: String?,
)

// ── Login ─────────────────────────────────────────────────────────
// UsuarioDto con id agregado para guardar en SharedPreferences
data class UsuarioDto(
    val id:     Int,       // ← necesario para guardar autor_id
    val nombre: String,
    val rol:    String,
    val sede:   String,
)