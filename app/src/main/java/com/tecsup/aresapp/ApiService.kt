package com.tecsup.aresapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/usuarios/login/") // ← Ajustado al estándar de rutas de DRF
    fun login(@Body request: LoginRequest): Call<LoginResponse>
}