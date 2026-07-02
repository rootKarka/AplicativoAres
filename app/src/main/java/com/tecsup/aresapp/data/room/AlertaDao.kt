package com.tecsup.aresapp.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerta(alerta: AlertaEntity): Long

    @Query("SELECT * FROM alertas ORDER BY fecha DESC")
    fun getAllAlertas(): Flow<List<AlertaEntity>>

    @Query("SELECT * FROM alertas ORDER BY fecha DESC")
    suspend fun getAllAlertasSync(): List<AlertaEntity>

    @Query("UPDATE alertas SET leida = 1 WHERE id = :id")
    suspend fun marcarComoLeida(id: Int): Int

    @Query("UPDATE alertas SET leida = 1")
    suspend fun marcarTodasComoLeidas(): Int

    @Query("SELECT COUNT(*) FROM alertas WHERE leida = 0")
    fun getCantNoLeidas(): Flow<Int>

    @Query("DELETE FROM alertas")
    suspend fun eliminarTodas(): Int
}
