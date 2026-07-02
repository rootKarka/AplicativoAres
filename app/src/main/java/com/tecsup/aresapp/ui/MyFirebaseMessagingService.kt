package com.tecsup.aresapp.ui

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tecsup.aresapp.R
import com.tecsup.aresapp.data.room.AlertaEntity
import com.tecsup.aresapp.data.room.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "Nuevo token FCM: $token")
        // Guardar token en preferencias para registrarlo después
        val prefs = getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        // Si ya hay sesión iniciada, enviar token a Django
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val autorId = prefs.getInt("autor_id", -1)
        if (isLoggedIn && autorId != -1) {
            enviarTokenAlServidor(autorId, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_SERVICE", "Mensaje FCM recibido de: ${remoteMessage.from}")

        // 1. Extraer datos del payload
        val data = remoteMessage.data
        val title = data["title"] ?: remoteMessage.notification?.title ?: "Alerta Crítica ARES"
        val body = data["body"] ?: remoteMessage.notification?.body ?: "Nueva anomalía detectada por el robot"
        val nivel = data["nivel"] ?: "CRITICO"
        val tipo = data["tipo"] ?: "GAS_TOXICO"
        val valor = data["valor"]?.toDoubleOrNull() ?: 0.0

        Log.d("FCM_SERVICE", "Datos: Titulo=$title, Body=$body, Nivel=$nivel, Tipo=$tipo, Valor=$valor")

        // 2. Guardar en Room de forma asíncrona
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val alerta = AlertaEntity(
                    nivel = nivel,
                    tipo = tipo,
                    mensaje = body,
                    valor = valor,
                    fecha = System.currentTimeMillis(),
                    leida = false
                )
                db.alertaDao().insertAlerta(alerta)
                Log.d("FCM_SERVICE", "Alerta guardada en Room exitosamente")
                
                // Enviar broadcast local para actualizar la UI en vivo
                val intentBroadcast = Intent("com.tecsup.aresapp.NUEVA_ALERTA").apply {
                    setPackage(packageName)
                }
                sendBroadcast(intentBroadcast)
            } catch (e: Exception) {
                Log.e("FCM_SERVICE", "Error guardando en Room: ${e.message}")
            }
        }

        // 3. Mostrar la notificación en Android
        mostrarNotificacionNativa(title, body)
    }

    @SuppressLint("MissingPermission")
    private fun mostrarNotificacionNativa(titulo: String, mensaje: String) {
        val channelId = "ARES_CRITICAL_ALERTS"
        
        // Crear intent para abrir MainActivity al tocar la notificación
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(this)
        
        // Verificar si tenemos permisos en Android 13+
        if (Build.VERSION.SDK_INT < 33 || 
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    private fun enviarTokenAlServidor(usuarioId: Int, token: String) {
        val request = com.tecsup.aresapp.data.TokenPushRequest(token)
        com.tecsup.aresapp.data.RetrofitClient.instance.registrarTokenPush(usuarioId, request)
            .enqueue(object : retrofit2.Callback<Void> {
                override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {
                    if (response.isSuccessful) {
                        Log.i("FCM_SERVICE", "Token FCM registrado con éxito en Django")
                    } else {
                        Log.e("FCM_SERVICE", "Fallo al registrar token en Django: ${response.code()}")
                    }
                }
                override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {
                    Log.e("FCM_SERVICE", "Error de red al registrar token FCM: ${t.message}")
                }
            })
    }
}
