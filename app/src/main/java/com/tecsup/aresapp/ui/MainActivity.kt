package com.tecsup.aresapp.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tecsup.aresapp.ui.components.BitacoraDialog
import com.tecsup.aresapp.R
import com.tecsup.aresapp.data.BitacoraDto
import com.tecsup.aresapp.data.BitacoraRequest
import com.tecsup.aresapp.data.EvidenciaDto
import com.tecsup.aresapp.data.MensajeLeidoRequest
import com.tecsup.aresapp.data.MensajeOperadorDto
import com.tecsup.aresapp.data.MensajeOperadorRequest
import com.tecsup.aresapp.data.RetrofitClient
import com.tecsup.aresapp.data.TokenPushRequest
import com.tecsup.aresapp.data.room.AppDatabase
import com.tecsup.aresapp.data.room.AlertaEntity
import com.tecsup.aresapp.databinding.ActivityMainBinding
import com.tecsup.aresapp.feature.login.LoginActivity
import com.tecsup.aresapp.feature.reporte.ReporteFragment
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var fabExpanded = false

    private var contadorNotificaciones = 0
    private var badgeDrawable: BadgeDrawable? = null
    private val CHANNEL_ID = "ARES_CRITICAL_ALERTS"
    private lateinit var alertaReceiver: AlertaAccionReceiver
    private lateinit var db: AppDatabase
    private lateinit var nuevaAlertaReceiver: BroadcastReceiver

    // ── WebSocket de Mensajería ──────────────────────────────────────
    private var mensajesWsClient: WebSocketClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.menuNav.setupWithNavController(navController)

        setupThemeIcon()
        setupTopBarMenu(navController)
        setupExpandableFab()
        setupNotificationBadge()
        crearCanalNotificaciones()
        registrarAlertaReceiver()
        conectarWebSocketMensajes()

        db = AppDatabase.getDatabase(this)
        registrarNuevaAlertaReceiver()
        observarAlertasNoLeidas()
        obtenerYEnviarFCMToken()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.nav_control || destination.id == R.id.nav_diagnostico) {
                if (fabExpanded) collapseFab()
                binding.fabMain.hide()
            } else {
                binding.fabMain.show()
            }
        }
    }

    fun updateThemeIcon() {
        val isNightMode = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val themeItem = binding.topAppBar.menu.findItem(R.id.nav_theme_toggle)
        themeItem?.setIcon(if (isNightMode) R.drawable.ic_moon else R.drawable.ic_sun)
    }

    private fun setupThemeIcon() {
        updateThemeIcon()
    }

    private fun setupTopBarMenu(navController: NavController) {
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_notifications -> {
                    mostrarHistorialNotificacionesYMensajes()
                    true
                }
                R.id.nav_cuenta -> {
                    navController.navigate(R.id.nav_cuenta)
                    true
                }
                R.id.nav_theme_toggle -> {
                    toggleThemeMode()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleThemeMode() {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES

        // Guardar preferencia
        val prefs = getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("dark_mode", !isNightMode).apply()

        // Cambiar tema
        if (isNightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        // Actualizar icono
        setupThemeIcon()
    }

    // ── Busca el ReporteFragment activo dentro del NavHostFragment ────
    // Devuelve null de forma segura si no está activo o no está agregado,
    // para evitar crashes al intentar refrescar su UI.
    private fun obtenerReporteFragmentActivo(): ReporteFragment? {
        return try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            val fragmentoActivo = navHostFragment
                ?.childFragmentManager
                ?.fragments
                ?.firstOrNull { it.isVisible }
            val reporteFragment = fragmentoActivo as? ReporteFragment
            if (reporteFragment != null && reporteFragment.isAdded) reporteFragment else null
        } catch (e: Exception) {
            Log.e("MainActivity", "Error obteniendo ReporteFragment: ${e.message}")
            null
        }
    }

    // ── WEBSOCKET DE MENSAJERÍA ────────────────────────────────────────
    // Cada operador escucha solo sus propios mensajes en:
    // ws/mensajes/{autorId}/
    private fun conectarWebSocketMensajes() {
        try {
            val prefs   = getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)
            val autorId = prefs.getInt("autor_id", 1)
            val wsUrl   = "wss://proyeecto-ares.onrender.com/ws/mensajes/$autorId/"

            mensajesWsClient = object : WebSocketClient(URI(wsUrl)) {
                override fun onOpen(handshake: ServerHandshake?) {
                    Log.d("WS_MENSAJES", "Conectado a $wsUrl")
                }

                override fun onMessage(message: String?) {
                    message ?: return
                    try {
                        val json = JSONObject(message)
                        runOnUiThread { procesarMensajeEntrante(json) }
                    } catch (e: Exception) {
                        Log.e("WS_MENSAJES", "Error parsing: ${e.message}")
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.d("WS_MENSAJES", "Cerrado: $reason")
                    // Reconectar a los 3 segundos
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!isFinishing && !isDestroyed) conectarWebSocketMensajes()
                    }, 3000)
                }

                override fun onError(ex: Exception?) {
                    Log.e("WS_MENSAJES", "Error: ${ex?.message}")
                }
            }
            mensajesWsClient?.connect()
        } catch (e: Exception) {
            Log.e("WS_MENSAJES", "No se pudo conectar: ${e.message}")
        }
    }

    // Muestra el mensaje entrante como notificación + diálogo con opción
    // de responder directamente.
    private fun procesarMensajeEntrante(json: JSONObject) {
        val mensajeId       = json.optInt("id", 0)
        val remitenteIdRaw  = json.optInt("remitente_id", 0)
        // Si el remitente viene en 0 (ausente o null en el JSON), usamos
        // el admin por defecto (id=1) para que la respuesta no falle.
        val remitenteId     = if (remitenteIdRaw == 0) 1 else remitenteIdRaw
        val remitenteNombre = json.optString("remitente_nombre", "Admin")
        val contenido       = json.optString("contenido", "")
        val tipo            = json.optString("tipo", "INFORMATIVO")
        val prioridad       = json.optString("prioridad", "NORMAL")

        incrementarNotificaciones()

        // Notificación del sistema (banner), reutilizando el canal crítico
        // si el mensaje es urgente/crítico.
        if (prioridad in listOf("URGENTE", "CRITICO")) {
            mostrarBannerAlertaCritica(
                id      = mensajeId,
                titulo  = "📨 $remitenteNombre — $tipo",
                mensaje = contenido
            )
        }

        // Diálogo en pantalla con opción de responder
        MaterialAlertDialogBuilder(this)
            .setTitle("📨 Mensaje de $remitenteNombre")
            .setMessage("[$tipo / $prioridad]\n\n$contenido")
            .setPositiveButton("Responder") { _, _ ->
                abrirDialogoResponderMensaje(remitenteId, contenido)
            }
            .setNegativeButton("Marcar como leído") { _, _ ->
                marcarMensajeComoLeido(mensajeId)
            }
            .setCancelable(true)
            .show()

        // Lo marcamos como leído automáticamente al mostrarlo
        marcarMensajeComoLeido(mensajeId)
    }

    private fun marcarMensajeComoLeido(mensajeId: Int) {
        if (mensajeId == 0) return
        RetrofitClient.instance.marcarMensajeLeido(mensajeId, MensajeLeidoRequest(true))
            .enqueue(object : Callback<MensajeOperadorDto> {
                override fun onResponse(call: Call<MensajeOperadorDto>, response: Response<MensajeOperadorDto>) {
                    Log.d("WS_MENSAJES", "Mensaje $mensajeId marcado como leído")
                }
                override fun onFailure(call: Call<MensajeOperadorDto>, t: Throwable) {
                    Log.e("WS_MENSAJES", "Error marcando leído: ${t.message}")
                }
            })
    }

    // ── Diálogo para que el operador escriba/responda un mensaje ──────
    private fun abrirDialogoResponderMensaje(destinatarioIdSugerido: Int? = null) {
        abrirDialogoResponderMensaje(destinatarioIdSugerido, null)
    }

    private fun abrirDialogoResponderMensaje(destinatarioIdSugerido: Int?, contextoOriginal: String?) {
        val inputEditText = EditText(this).apply {
            hint = "Escribe tu respuesta al administrador..."
            setPadding(50, 40, 50, 40)
            if (!contextoOriginal.isNullOrBlank()) {
                hint = "Responder a: \"$contextoOriginal\""
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("✍️ Responder al Admin")
            .setView(inputEditText)
            .setPositiveButton("Enviar") { _, _ ->
                val texto = inputEditText.text.toString().trim()
                if (texto.isNotBlank()) {
                    enviarMensajeAlAdmin(texto, destinatarioIdSugerido)
                } else {
                    Toast.makeText(this, "El mensaje no puede estar vacío", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Envía la respuesta del operador hacia el Admin (React) vía Django.
    // remitente = operador actual, destinatario = admin que escribió
    // (o el id por defecto 1 si no se conoce, ajustar según tu lógica de roles).
    private fun enviarMensajeAlAdmin(contenido: String, destinatarioId: Int?) {
        val prefs    = getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)
        val autorId  = prefs.getInt("autor_id",  1)
        val misionId = prefs.getInt("mision_id", 1)

        val request = MensajeOperadorRequest(
            remitente    = autorId,
            destinatario = destinatarioId ?: 1,
            mision       = misionId,
            tipo         = "INFORMATIVO",
            contenido    = contenido,
            prioridad    = "NORMAL",
        )

        RetrofitClient.instance.postMensaje(request).enqueue(object : Callback<MensajeOperadorDto> {
            override fun onResponse(call: Call<MensajeOperadorDto>, response: Response<MensajeOperadorDto>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "📨 Mensaje enviado al Administrador", Toast.LENGTH_SHORT).show()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("MENSAJE", "Error 400 detalle: $errorBody")
                    Toast.makeText(this@MainActivity, "❌ Error: ${response.code()} - $errorBody", Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<MensajeOperadorDto>, t: Throwable) {
                Toast.makeText(this@MainActivity, "⚠️ Sin conexión: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun setupExpandableFab() {
        binding.fabMain.setOnClickListener {
            fabExpanded = !fabExpanded
            if (fabExpanded) expandFab() else collapseFab()
        }

        // ── Mensaje al administrador ──────────────────────────────
        binding.fabMensaje.setOnClickListener {
            collapseFab()
            abrirDialogoResponderMensaje(destinatarioIdSugerido = 1)
        }

        // ── Cámara de entorno ─────────────────────────────────────
        binding.fabCamaraEntorno.setOnClickListener {
            collapseFab()
            verificarPermisoYAbrirCamara()
        }

        // ── Bitácora — abre el dialog con misionId y autorId ──────
        binding.fabMicBitacora.setOnClickListener {
            collapseFab()

            // Leer datos de sesión guardados en el login
            val prefs    = getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)
            val misionId = prefs.getInt("mision_id", 1)
            val autorId  = prefs.getInt("autor_id",  1)

            val dialog = BitacoraDialog.newInstance(misionId, autorId)
            dialog.onEntradaGuardada = { texto, tipo, esVoz ->
                val label = if (esVoz) "🎤 Voz" else "📝 Texto"
                Toast.makeText(this, "$label guardada en bitácora", Toast.LENGTH_SHORT).show()

                // Refrescar la bitácora visible en ReporteFragment si está activo,
                // envuelto en try-catch para evitar que un fallo aquí tumbe la app.
                try {
                    obtenerReporteFragmentActivo()?.cargarBitacoraDesdjango()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error refrescando bitácora: ${e.message}")
                }
            }
            dialog.show(supportFragmentManager, BitacoraDialog.TAG)
        }
    }

    @OptIn(ExperimentalBadgeUtils::class)
    private fun setupNotificationBadge() {
        binding.topAppBar.post {
            badgeDrawable = BadgeDrawable.create(this).apply {
                backgroundColor = ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark)
                badgeTextColor  = ContextCompat.getColor(this@MainActivity, android.R.color.white)
                isVisible = false
            }
            BadgeUtils.attachBadgeDrawable(badgeDrawable!!, binding.topAppBar, R.id.action_notifications)
        }
    }

    fun incrementarNotificaciones() {
        contadorNotificaciones++
        badgeDrawable?.apply {
            number    = contadorNotificaciones
            isVisible = true
        }
    }

    fun limpiarNotificaciones() {
        contadorNotificaciones = 0
        badgeDrawable?.isVisible = false
    }

    fun ejecutarCierreDeSesion() {
        // Limpiar sesión al cerrar
        getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun registrarNuevaAlertaReceiver() {
        nuevaAlertaReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // El contador se actualizará por Flow en observarAlertasNoLeidas()
                Toast.makeText(this@MainActivity, "🚨 Nueva alerta crítica recibida!", Toast.LENGTH_SHORT).show()
            }
        }
        val filter = IntentFilter("com.tecsup.aresapp.NUEVA_ALERTA")
        ContextCompat.registerReceiver(
            this, nuevaAlertaReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun observarAlertasNoLeidas() {
        lifecycleScope.launch {
            db.alertaDao().getCantNoLeidas().collect { unreadCount ->
                contadorNotificaciones = unreadCount
                runOnUiThread {
                    badgeDrawable?.apply {
                        number = unreadCount
                        isVisible = unreadCount > 0
                    }
                }
            }
        }
    }

    private fun obtenerYEnviarFCMToken() {
        val prefs = getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)
        val autorId = prefs.getInt("autor_id", -1)
        if (autorId == -1) return

        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("MainActivity", "Fallo al obtener token FCM", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d("MainActivity", "Token FCM obtenido: $token")
                
                prefs.edit().putString("fcm_token", token).apply()
                enviarTokenAlServidor(autorId, token)
            }
    }

    private fun enviarTokenAlServidor(usuarioId: Int, token: String) {
        val request = TokenPushRequest(token)
        RetrofitClient.instance.registrarTokenPush(usuarioId, request)
            .enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        Log.i("MainActivity", "Token FCM registrado con éxito en Django")
                    } else {
                        Log.e("MainActivity", "Fallo al registrar token FCM: ${response.code()}")
                    }
                }
                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.e("MainActivity", "Error de red al registrar token FCM: ${t.message}")
                }
            })
    }

    private fun mostrarHistorialNotificacionesYMensajes() {
        limpiarNotificaciones()
        lifecycleScope.launch {
            val alertas = db.alertaDao().getAllAlertasSync()
            runOnUiThread {
                if (alertas.isEmpty()) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("🔔 Historial de Alertas")
                        .setMessage("No hay alertas de sensores registradas en la base local.")
                        .setPositiveButton("Entendido", null)
                        .show()
                } else {
                    val alertItems = alertas.map { alerta ->
                        val formatoFecha = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
                        val fechaStr = formatoFecha.format(Date(alerta.fecha))
                        val indicador = if (alerta.leida) "✓" else "●"
                        "$indicador [$fechaStr] [${alerta.nivel}] ${alerta.tipo}\n${alerta.mensaje}"
                    }.toTypedArray()

                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("🔔 Alertas Recientes")
                        .setItems(alertItems) { dialog, which ->
                            val selectedAlerta = alertas[which]
                            lifecycleScope.launch {
                                db.alertaDao().marcarComoLeida(selectedAlerta.id)
                            }
                            Toast.makeText(this@MainActivity, "Alerta marcada como leída", Toast.LENGTH_SHORT).show()
                        }
                        .setPositiveButton("Marcar leídas") { _, _ ->
                            lifecycleScope.launch {
                                db.alertaDao().marcarTodasComoLeidas()
                            }
                        }
                        .setNegativeButton("Borrar todo") { _, _ ->
                            lifecycleScope.launch {
                                db.alertaDao().eliminarTodas()
                            }
                        }
                        .setNeutralButton("Cerrar", null)
                        .show()
                }
            }
        }
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertas Críticas ARES",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de alta prioridad para control de riesgos y fugas."
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun registrarAlertaReceiver() {
        alertaReceiver = AlertaAccionReceiver()
        val filter = IntentFilter("com.tecsup.aresapp.ACTION_ALERTA_ENTENDIDO")
        ContextCompat.registerReceiver(
            this, alertaReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    @SuppressLint("MissingPermission")
    fun mostrarBannerAlertaCritica(id: Int, titulo: String, mensaje: String) {
        val intentEntendido = Intent("com.tecsup.aresapp.ACTION_ALERTA_ENTENDIDO").apply {
            putExtra("ALERTA_ID", id)
            setPackage(packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, id, intentEntendido,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_bucle, "ENTENDIDO (PROCESAR)", pendingIntent)

        val notificationManager = NotificationManagerCompat.from(this)
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33) {
                notificationManager.notify(id, builder.build())
                incrementarNotificaciones()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { esConcedido ->
        if (esConcedido) abrirCamaraEntorno()
        else Toast.makeText(this, "⚠️ Se requiere el permiso de cámara.", Toast.LENGTH_LONG).show()
    }

    private fun verificarPermisoYAbrirCamara() {
        val estadoPermiso = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (estadoPermiso == PackageManager.PERMISSION_GRANTED) abrirCamaraEntorno()
        else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private var fotoUriActual: Uri? = null
    private var fotoArchivoActual: File? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { exito ->
        if (exito && fotoArchivoActual != null) {
            Toast.makeText(this, "📸 Foto del entorno guardada", Toast.LENGTH_SHORT).show()
            subirFotoEntorno(fotoArchivoActual!!)
        } else {
            Toast.makeText(this, "❌ Se canceló la captura", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirCamaraEntorno() {
        try {
            val archivo = crearArchivoTemporal()
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", archivo)
            fotoUriActual     = uri
            fotoArchivoActual = archivo
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Error inesperado: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun crearArchivoTemporal(): File {
        val carpeta = getExternalFilesDir("Pictures/ARES")
        if (carpeta != null && !carpeta.exists()) carpeta.mkdirs()
        val nombre = "ENTORNO_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        return File(carpeta, nombre)
    }

    // ── Sube la foto de la cámara de entorno como evidencia en Django ──
    private fun subirFotoEntorno(archivo: File) {
        val prefs    = getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)
        val misionId = prefs.getInt("mision_id", 1)
        val autorId  = prefs.getInt("autor_id",  1)

        try {
            val misionBody  = misionId.toString()
                .toRequestBody("text/plain".toMediaTypeOrNull())
            val usuarioBody = autorId.toString()
                .toRequestBody("text/plain".toMediaTypeOrNull())
            val tipoBody    = "FOTO".toRequestBody("text/plain".toMediaTypeOrNull())
            val archivoPart = MultipartBody.Part.createFormData(
                "archivo", archivo.name,
                archivo.asRequestBody("image/*".toMediaTypeOrNull())
            )

            RetrofitClient.instance.postEvidencia(misionBody, usuarioBody, tipoBody, archivoPart)
                .enqueue(object : Callback<EvidenciaDto> {
                    override fun onResponse(call: Call<EvidenciaDto>, response: Response<EvidenciaDto>) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@MainActivity, "✅ Evidencia subida a Django", Toast.LENGTH_SHORT).show()
                            crearEntradaBitacoraPorFoto(misionId, autorId)
                        } else {
                            Toast.makeText(this@MainActivity, "Error subiendo evidencia: ${response.code()}", Toast.LENGTH_LONG).show()
                        }
                    }
                    override fun onFailure(call: Call<EvidenciaDto>, t: Throwable) {
                        Toast.makeText(this@MainActivity, "Sin conexión: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                })
        } catch (e: Exception) {
            Log.e("CAMARA", "Error subiendo foto: ${e.message}")
        }
    }

    // ── Crea una entrada en bitácora cuando se sube una foto de entorno ──
    private fun crearEntradaBitacoraPorFoto(misionId: Int, autorId: Int) {
        try {
            RetrofitClient.instance.postBitacora(
                BitacoraRequest(
                    mision       = misionId,
                    usuario      = autorId,
                    tipo_entrada = "HALLAZGO",
                    contenido    = "📷 Foto de entorno capturada",
                    es_voz       = false,
                )
            ).enqueue(object : Callback<BitacoraDto> {
                override fun onResponse(call: Call<BitacoraDto>, response: Response<BitacoraDto>) {
                    try {
                        obtenerReporteFragmentActivo()?.cargarBitacoraDesdjango()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error refrescando bitácora tras foto: ${e.message}")
                    }
                }
                override fun onFailure(call: Call<BitacoraDto>, t: Throwable) {
                    Log.e("API", "Error creando entrada bitácora por foto: ${t.message}")
                }
            })
        } catch (e: Exception) {
            Log.e("API", "Error inesperado creando entrada de bitácora: ${e.message}")
        }
    }

    private fun expandFab() {
        binding.fabMensaje.visibility      = View.VISIBLE
        binding.fabCamaraEntorno.visibility = View.VISIBLE
        binding.fabMicBitacora.visibility  = View.VISIBLE
        binding.fabMensaje.animate().alpha(1f).setDuration(150).start()
        binding.fabCamaraEntorno.animate().alpha(1f).setDuration(150).start()
        binding.fabMicBitacora.animate().alpha(1f).setDuration(150).start()
        binding.fabMain.animate().rotation(45f).setDuration(150).start()
    }

    private fun collapseFab() {
        fabExpanded = false
        binding.fabMensaje.animate().alpha(0f).setDuration(150).withEndAction {
            binding.fabMensaje.visibility = View.INVISIBLE
        }.start()
        binding.fabCamaraEntorno.animate().alpha(0f).setDuration(150).withEndAction {
            binding.fabCamaraEntorno.visibility = View.INVISIBLE
        }.start()
        binding.fabMicBitacora.animate().alpha(0f).setDuration(150).withEndAction {
            binding.fabMicBitacora.visibility = View.INVISIBLE
        }.start()
        binding.fabMain.animate().rotation(0f).setDuration(150).start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mensajesWsClient?.close()
        unregisterReceiver(alertaReceiver)
        if (::nuevaAlertaReceiver.isInitialized) {
            unregisterReceiver(nuevaAlertaReceiver)
        }
    }

    inner class AlertaAccionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val alertaId = intent?.getIntExtra("ALERTA_ID", 0) ?: 0
            Toast.makeText(context, "✅ Alerta #$alertaId Confirmada", Toast.LENGTH_SHORT).show()
            (context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(alertaId)
        }
    }
}