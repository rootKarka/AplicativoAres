package com.tecsup.aresapp.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://proyeecto-ares.onrender.com/"

    // ── Interceptor de reintentos ───────────────────────────────────
    // El plan gratuito de PostgreSQL en Render a veces cierra conexiones
    // SSL de forma intermitente bajo carga (error 500 "connection closed
    // unexpectedly"). Como suele resolverse en el siguiente intento,
    // reintentamos automáticamente 1 vez si la respuesta es un 500.
    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var response = chain.proceed(request)

        var intentos = 0
        val maxIntentos = 2 // total: 1 intento original + 2 reintentos

        while (!response.isSuccessful && response.code == 500 && intentos < maxIntentos) {
            response.close()
            intentos++
            Thread.sleep(800L * intentos) // 800ms, luego 1600ms
            response = chain.proceed(request)
        }

        response
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(retryInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}