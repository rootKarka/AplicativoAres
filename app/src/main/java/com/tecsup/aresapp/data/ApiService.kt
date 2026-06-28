package com.tecsup.aresapp.data

import com.tecsup.aresapp.feature.login.LoginRequest
import com.tecsup.aresapp.feature.login.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("api/usuarios/login/")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

}