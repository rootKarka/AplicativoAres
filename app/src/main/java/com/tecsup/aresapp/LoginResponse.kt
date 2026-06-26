package com.tecsup.aresapp

data class LoginResponse(
    val status: String,
    val message: String,
    val usuario: UsuarioDto?
)

data class UsuarioDto(
    val nombre: String,
    val rol: String,
    val sede: String
)