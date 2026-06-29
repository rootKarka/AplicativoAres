package com.tecsup.aresapp.feature.login

data class LoginResponse(
    val status: String,
    val message: String,
    val usuario: UsuarioDto?
)

data class UsuarioDto(
    val id:     Int,      // ← agregar
    val nombre: String,
    val rol:    String,
    val sede:   String,
)