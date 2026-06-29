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
import com.tecsup.aresapp.data.RetrofitClient
import com.tecsup.aresapp.databinding.ActivityMainBinding
import com.tecsup.aresapp.feature.login.LoginActivity
import kotlinx.coroutines.launch
import java.io.File
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

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.nav_control) {
                if (fabExpanded) collapseFab()
                binding.fabMain.hide()
            } else {
                binding.fabMain.show()
            }
        }
    }

    private fun setupThemeIcon() {
        val isNightMode = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val themeItem = binding.topAppBar.menu.findItem(R.id.nav_theme_toggle)
        themeItem?.setIcon(if (isNightMode) R.drawable.ic_moon else R.drawable.ic_sun)
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
        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun setupExpandableFab() {
        binding.fabMain.setOnClickListener {
            fabExpanded = !fabExpanded
            if (fabExpanded) expandFab() else collapseFab()
        }

        // ── Mensaje al administrador ──────────────────────────────
        binding.fabMensaje.setOnClickListener {
            collapseFab()
            val inputEditText = EditText(this).apply {
                hint = "Escribe un mensaje urgente al administrador..."
                setPadding(50, 40, 50, 40)
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("💬 Mensaje Directo al Admin")
                .setMessage("Reporta incidencias operativas de la misión:")
                .setView(inputEditText)
                .setPositiveButton("Enviar") { _, _ ->
                    val textoMensaje = inputEditText.text.toString()
                    if (textoMensaje.isNotBlank()) {
                        enviarMensajeAlBackend(textoMensaje)
                    } else {
                        Toast.makeText(this, "El mensaje no puede estar vacío", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
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
            }
            dialog.show(supportFragmentManager, BitacoraDialog.TAG)
        }
    }

    private fun enviarMensajeAlBackend(contenido: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Enviando reporte...", Toast.LENGTH_SHORT).show()
                val backendExitoso = true
                if (backendExitoso) {
                    Toast.makeText(this@MainActivity, "📨 Mensaje enviado al Administrador", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "❌ El servidor rechazó el mensaje", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al enviar mensaje: ${e.message}", e)
                Toast.makeText(this@MainActivity, "⚠️ Error de red.", Toast.LENGTH_LONG).show()
            }
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

    private fun mostrarHistorialNotificacionesYMensajes() {
        limpiarNotificaciones()
        MaterialAlertDialogBuilder(this)
            .setTitle("🔔 Centro de Alertas Recientes")
            .setMessage("No tienes nuevas instrucciones críticas pendientes del Administrador.")
            .setPositiveButton("Entendido", null)
            .show()
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

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { exito ->
        if (exito && fotoUriActual != null) {
            Toast.makeText(this, "📸 Foto del entorno guardada", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ Se canceló la captura", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirCamaraEntorno() {
        try {
            val archivo = crearArchivoTemporal()
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", archivo)
            fotoUriActual = uri
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
        unregisterReceiver(alertaReceiver)
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