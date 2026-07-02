package com.tecsup.aresapp.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alertas")
data class AlertaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val nivel: String,       // INFO, ADVERTENCIA, CRITICO, EMERGENCIA
    val tipo: String,        // GAS_TOXICO, INCENDIO, etc.
    val mensaje: String,
    val valor: Double,
    val fecha: Long = System.currentTimeMillis(),
    val leida: Boolean = false
)
