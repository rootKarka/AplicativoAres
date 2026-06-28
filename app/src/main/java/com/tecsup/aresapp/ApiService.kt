package com.tecsup.aresapp

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

interface ApiService {

    // ── Login ─────────────────────────────────────────────────────
    @POST("api/usuarios/login/")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    // ── Bitácora ──────────────────────────────────────────────────
    @GET("api/bitacora/")
    fun getBitacora(@Query("mision") misionId: Int): Call<List<BitacoraDto>>

    @POST("api/bitacora/")
    fun postBitacora(@Body body: BitacoraRequest): Call<BitacoraDto>

    // ── Reporte Actualización ─────────────────────────────────────
    @POST("api/reportes/actualizacion/")
    fun postReporteActualizacion(@Body body: ReporteActualizacionRequest): Call<ReporteActualizacionDto>

    // ── Reporte Final ─────────────────────────────────────────────
    @POST("api/reportes/final/")
    fun postReporteFinal(@Body body: ReporteFinalRequest): Call<ReporteFinalDto>

    // ── Resumen Misión (tab Finalizar) ────────────────────────────
    @GET("api/misiones/{id}/resumen/")
    fun getResumenMision(@Path("id") misionId: Int): Call<ResumenMisionDto>

    // ── Evidencia fotográfica ─────────────────────────────────────
    @Multipart
    @POST("api/evidencias/")
    fun postEvidencia(
        @Part("mision")   mision:   RequestBody,
        @Part("usuario")  usuario:  RequestBody,
        @Part("tipo")     tipo:     RequestBody,
        @Part            archivo:  MultipartBody.Part
    ): Call<EvidenciaDto>
}