package com.tecsup.aresapp

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.tecsup.aresapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var fabExpanded = false

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

        // ── NUEVO: Listener para ocultar el FAB en el Control ──
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Revisa que R.id.nav_control sea el ID real de tu fragmento en tu nav_graph.xml
            if (destination.id == R.id.nav_control) {
                // Si entramos al Control, contraemos el menú (por si estaba abierto) y lo ocultamos
                if (fabExpanded) collapseFab()
                binding.fabMain.hide() // .hide() lo oculta con una animación suave
            } else {
                // Si estamos en cualquier otra pantalla (Mapa, Ajustes, etc.), lo mostramos
                binding.fabMain.show() // .show() lo aparece con animación
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

        binding.fabCamaraEntorno.setOnClickListener {
            collapseFab()
            verificarPermisoYAbrirCamara() // ← Ahora llama a la verificación primero
        }

        binding.fabMicBitacora.setOnClickListener {
            collapseFab()
            BitacoraDialog().show(supportFragmentManager, BitacoraDialog.TAG)
        }
    }

    // ── GESTIÓN DE PERMISOS EN TIEMPO DE EJECUCIÓN ──

    // Este launcher maneja la respuesta del usuario al cartel de "Permitir que ARES use la cámara"
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { esConcedido ->
        if (esConcedido) {
            // El usuario dio permiso por primera vez, abrimos la cámara
            abrirCamaraEntorno()
        } else {
            // El usuario rechazó el permiso
            android.widget.Toast.makeText(
                this,
                "⚠️ Se requiere el permiso de cámara para registrar evidencias del entorno.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun verificarPermisoYAbrirCamara() {
        val estadoPermiso = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

        if (estadoPermiso == PackageManager.PERMISSION_GRANTED) {
            // Si el permiso ya está aprobado de antes, abre la cámara directo
            abrirCamaraEntorno()
        } else {
            // Si no tiene el permiso, lanza el cartel flotante del sistema
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }


    // ── CÁMARA DEL OPERADOR: INTENT IMPLÍCITO NATIVO ──
    private var fotoUriActual: android.net.Uri? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { exito ->
        if (exito && fotoUriActual != null) {
            android.widget.Toast.makeText(this, "📸 Foto del entorno guardada", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(this, "❌ Se canceló la captura", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirCamaraEntorno() {
        try {
            val archivo = crearArchivoTemporal()
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                archivo
            )
            fotoUriActual = uri
            cameraLauncher.launch(uri)

        } catch (e: IllegalArgumentException) {
            android.widget.Toast.makeText(this, "Error Provider: Revisa que el archivo sea file_paths.xml", android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, "Error: No se detectó ninguna app de cámara", android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Error inesperado: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun crearArchivoTemporal(): java.io.File {
        val carpeta = getExternalFilesDir("Pictures/ARES")
        if (carpeta != null && !carpeta.exists()) carpeta.mkdirs()
        val nombre = "ENTORNO_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}.jpg"
        return java.io.File(carpeta, nombre)
    }

    private fun expandFab() {
        binding.fabCamaraEntorno.visibility = android.view.View.VISIBLE
        binding.fabMicBitacora.visibility = android.view.View.VISIBLE
        binding.fabCamaraEntorno.animate().alpha(1f).setDuration(150).start()
        binding.fabMicBitacora.animate().alpha(1f).setDuration(150).start()
        binding.fabMain.animate().rotation(45f).setDuration(150).start()
    }

    private fun collapseFab() {
        fabExpanded = false
        binding.fabCamaraEntorno.animate().alpha(0f).setDuration(150).withEndAction {
            binding.fabCamaraEntorno.visibility = android.view.View.INVISIBLE
        }.start()
        binding.fabMicBitacora.animate().alpha(0f).setDuration(150).withEndAction {
            binding.fabMicBitacora.visibility = android.view.View.INVISIBLE
        }.start()
        binding.fabMain.animate().rotation(0f).setDuration(150).start()
    }
}