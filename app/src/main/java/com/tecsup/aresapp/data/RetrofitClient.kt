package com.tecsup.aresapp.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Si usas el emulador de Android, '10.0.2.2' apunta al localhost de tu PC
    // En el futuro, mover esto a un archivo de configuración (BuildConfig)
    private const val BASE_URL = "http://10.147.188.78:8000/"

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}