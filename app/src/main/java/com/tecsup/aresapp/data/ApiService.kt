package com.tecsup.aresapp.data

import com.tecsup.aresapp.feature.login.LoginRequest
import com.tecsup.aresapp.feature.login.LoginResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Login (de tu compañero — suspend + Response) ──────────────
    @POST("api/usuarios/login/")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ── Bitácora ─────────
    // ─────────────────────────────────────────
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

    // ── Cerrar Misión (después del reporte final) ─────────────────
    @POST("api/misiones/{id}/cerrar/")
    fun cerrarMision(@Path("id") misionId: Int): Call<MisionCerradaDto>

    // ── Evidencia fotográfica ─────────────────────────────────────
    @Multipart
    @POST("api/evidencias/")
    fun postEvidencia(
        @Part("mision")  mision:  RequestBody,
        @Part("usuario") usuario: RequestBody,
        @Part("tipo")    tipo:    RequestBody,
        @Part           archivo: MultipartBody.Part
    ): Call<EvidenciaDto>
}