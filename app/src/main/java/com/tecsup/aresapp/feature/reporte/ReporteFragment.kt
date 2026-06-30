package com.tecsup.aresapp.feature.reporte

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tecsup.aresapp.R
import com.tecsup.aresapp.data.BitacoraDto
import com.tecsup.aresapp.data.BitacoraRequest
import com.tecsup.aresapp.data.EvidenciaDto
import com.tecsup.aresapp.data.ReporteActualizacionDto
import com.tecsup.aresapp.data.ReporteActualizacionRequest
import com.tecsup.aresapp.data.ReporteFinalDto
import com.tecsup.aresapp.data.ReporteFinalRequest
import com.tecsup.aresapp.data.ResumenMisionDto
import com.tecsup.aresapp.data.RetrofitClient
import com.tecsup.aresapp.databinding.FragmentReporteBinding
import com.tecsup.aresapp.data.MisionCerradaDto
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

class ReporteFragment : Fragment() {

    private var _binding: FragmentReporteBinding? = null
    private val binding get() = _binding!!

    private val pA get() = binding.panelActualizacion
    private val pF get() = binding.panelFinalizar

    // ── API ───────────────────────────────────────────────────────
    private val api get() = RetrofitClient.instance
    private val WS_URL = "wss://proyeecto-ares.onrender.com/ws/sensores/"

    // ── Datos de sesión (leídos desde SharedPreferences) ───────────
    private var misionId = 1
    private var autorId  = 1

    // ── WebSocket ─────────────────────────────────────────────────
    private var wsClient: WebSocketClient? = null
    private val horaInicioMision = System.currentTimeMillis()

    // ── Galería ───────────────────────────────────────────────────
    private var fotoUri: Uri? = null
    private val galeriaLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                fotoUri = uri
                mostrarFotoSeleccionada(uri)
            }
        }
    }

    // ── Contadores Actualización ──────────────────────────────────
    private var actHeridas    = 0
    private var actFallecidas = 0
    private var actRescatadas = 0

    // ── Contadores Finalizar ──────────────────────────────────────
    private var finHeridas      = 0
    private var finFallecidas   = 0
    private var finRescatadas   = 0
    private var finSinConfirmar = 0

    // ── Gravedad ──────────────────────────────────────────────────
    private var gravedadActualizacion = "NORMAL"
    private var gravedadFinal         = "CRITICO"

    // ── Bitácora ──────────────────────────────────────────────────
    data class EntradaBitacora(val hora: String, val texto: String, val tipo: String)
    private val bitacoraEntradas = mutableListOf<EntradaBitacora>()

    // ─────────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReporteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── Leer misionId/autorId desde la sesión guardada en el login ──
        val prefs = requireActivity().getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)
        misionId  = prefs.getInt("mision_id", 1)
        autorId   = prefs.getInt("autor_id",  1)

        setupTabs()
        setupGravedadActualizacion()
        setupGravedadFinalizar()
        setupContadoresActualizacion()
        setupContadoresFinal()
        setupBotones()
        actualizarHora()
        renderizarBitacora()
        conectarWebSocket()
        cargarBitacoraDesdjango()
    }

    // ── TABS ──────────────────────────────────────────────────────
    private fun setupTabs() {
        setTabActualizacionActivo(true)
        binding.tabActualizacion.setOnClickListener { setTabActualizacionActivo(true) }
        binding.tabFinalizar.setOnClickListener {
            setTabActualizacionActivo(false)
            cargarDatosMisionFinal()
        }
    }

    private fun setTabActualizacionActivo(activo: Boolean) {
        if (activo) {
            binding.tabActualizacion.setBackgroundResource(R.drawable.bg_tab_orange)
            binding.tvTabActualizacion.setTextColor(color(R.color.ares_black))
            binding.tabFinalizar.setBackgroundResource(android.R.color.transparent)
            binding.tvTabFinalizar.setTextColor(color(R.color.ares_grey))
            pA.layoutContenidoActualizacion.visibility = View.VISIBLE
            pF.layoutContenidoFinalizar.visibility     = View.GONE
        } else {
            binding.tabFinalizar.setBackgroundResource(R.drawable.bg_tab_orange)
            binding.tvTabFinalizar.setTextColor(color(R.color.ares_black))
            binding.tabActualizacion.setBackgroundResource(android.R.color.transparent)
            binding.tvTabActualizacion.setTextColor(color(R.color.ares_grey))
            pF.layoutContenidoFinalizar.visibility     = View.VISIBLE
            pA.layoutContenidoActualizacion.visibility = View.GONE
            actualizarDuracionMision()
        }
    }

    // ── WEBSOCKET ─────────────────────────────────────────────────
    private var intentosReconexionWs = 0

    private fun conectarWebSocket() {
        try {
            wsClient = object : WebSocketClient(URI(WS_URL)) {

                override fun onOpen(handshake: ServerHandshake?) {
                    Log.d("WS", "Conectado a $WS_URL")
                    intentosReconexionWs = 0 // resetear contador al conectar bien
                }

                override fun onMessage(message: String?) {
                    message ?: return
                    try {
                        val json = JSONObject(message)
                        requireActivity().runOnUiThread { procesarMensajeWS(json) }
                    } catch (e: Exception) {
                        Log.e("WS", "Error parsing: ${e.message}")
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.d("WS", "Cerrado: $reason")
                    // Backoff progresivo: 2s, 4s, 6s... hasta 10s máximo,
                    // para no saturar la conexión a PostgreSQL con reintentos
                    // demasiado agresivos cuando el servidor está inestable.
                    intentosReconexionWs++
                    val delayMs = (1500L * intentosReconexionWs).coerceAtMost(5000L)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isAdded) conectarWebSocket()
                    }, delayMs)
                }

                override fun onError(ex: Exception?) {
                    Log.e("WS", "Error: ${ex?.message}")
                }
            }
            wsClient?.connect()
        } catch (e: Exception) {
            Log.e("WS", "No se pudo conectar: ${e.message}")
        }
    }

    private fun procesarMensajeWS(json: JSONObject) {
        when {
            // ── Alerta desde Spring Boot ──────────────────────────
            json.optBoolean("es_alerta") -> {
                val nivel   = json.optString("nivel",   "")
                val tipo    = json.optString("tipo",    "")
                val mensaje = json.optString("mensaje", "")
                val valor   = json.optDouble("valor",   0.0)

                // Resaltar gravedad automáticamente
                resaltarGravedadDesdeRobot(nivel)

                // Agregar a bitácora local
                val tipoEntrada = when (nivel) {
                    "CRITICO", "EMERGENCIA" -> "INCIDENTE"
                    "ADVERTENCIA"           -> "EVENTO"
                    else                    -> "NOTA"
                }
                agregarEntradaBitacoraLocal(
                    "⚠ $mensaje (${String.format("%.1f", valor)})",
                    tipoEntrada
                )

                // Vibración háptica para alertas críticas
                if (nivel in listOf("CRITICO", "EMERGENCIA")) {
                    val vibrator = requireContext()
                        .getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 500, 200, 500), -1
                        )
                    )
                }
            }

            // ── Telemetría del robot (batería, latencia, GPS) ─────
            json.optBoolean("es_telemetria") -> {
                val bateria  = json.optDouble("bateria",    0.0)
                val latencia = json.optInt("latencia_ms",   0)

                pF.tvMisionBateria.text  = "${bateria.toInt()}%"
                pF.tvMisionLatencia.text = "${latencia}ms"

                pF.tvMisionBateria.setTextColor(color(when {
                    bateria < 20 -> R.color.ares_red
                    bateria < 50 -> R.color.ares_yellow
                    else         -> R.color.ares_white
                }))
                pF.tvMisionLatencia.setTextColor(color(when {
                    latencia > 200 -> R.color.ares_red
                    latencia > 100 -> R.color.ares_yellow
                    else           -> R.color.ares_white
                }))
            }

            // ── Lectura de sensor (gas, temperatura) ──────────────
            // Umbrales alineados con AnalisisService.java de Spring
            else -> {
                val tipo  = json.optString("tipo",  "")
                val valor = json.optDouble("valor", 0.0)

                when (tipo.uppercase()) {
                    "GAS" -> {
                        pA.tvGas.text = String.format("%.1f PPM", valor)
                        pA.tvGas.setTextColor(color(when {
                            valor > 150 -> R.color.ares_red     // Spring: CRITICO
                            valor > 50  -> R.color.ares_yellow  // Spring: ADVERTENCIA
                            else        -> R.color.ares_green
                        }))
                    }
                    "TEMPERATURA" -> {
                        pA.tvTemp.text = String.format("%.1f °C", valor)
                        pA.tvTemp.setTextColor(color(when {
                            valor > 42 -> R.color.ares_red      // Spring: EMERGENCIA
                            valor > 35 -> R.color.ares_yellow   // Spring: ADVERTENCIA
                            else       -> R.color.ares_green
                        }))
                    }
                }
            }
        }
    }

    // ── CARGAR DATOS MISIÓN FINAL (Retrofit GET) ──────────────────
    private fun cargarDatosMisionFinal() {
        api.getResumenMision(misionId).enqueue(object : Callback<ResumenMisionDto> {
            override fun onResponse(call: Call<ResumenMisionDto>, response: Response<ResumenMisionDto>) {
                if (!isAdded) return
                response.body()?.let { resumen ->
                    pF.tvMisionBateria.text  = "${resumen.bateria_inicio.toInt()}%"
                    pF.tvMisionLatencia.text = "${resumen.latencia_promedio_ms}ms"
                    pF.tvMisionAlertas.text  = "${resumen.alertas_criticas} Críticas / ${resumen.total_alertas} total"
                    actualizarDuracionMision()
                }
            }
            override fun onFailure(call: Call<ResumenMisionDto>, t: Throwable) {
                Log.e("API", "Error resumen: ${t.message}")
            }
        })
    }

    // ── CARGAR BITÁCORA (Retrofit GET) ────────────────────────────
    // Público para poder refrescarse desde MainActivity cuando se guarda
    // una nueva entrada desde BitacoraDialog (texto, voz o foto).
    fun cargarBitacoraDesdjango() {
        api.getBitacora(misionId).enqueue(object : Callback<List<BitacoraDto>> {
            override fun onResponse(call: Call<List<BitacoraDto>>, response: Response<List<BitacoraDto>>) {
                if (!isAdded) return
                response.body()?.let { lista ->
                    bitacoraEntradas.clear()
                    lista.forEach { item ->
                        bitacoraEntradas.add(
                            EntradaBitacora(
                                hora  = formatearFecha(item.fecha),
                                texto = item.contenido,
                                tipo  = item.tipo_entrada
                            )
                        )
                    }
                    renderizarBitacora()
                }
            }
            override fun onFailure(call: Call<List<BitacoraDto>>, t: Throwable) {
                Log.e("API", "Error bitácora: ${t.message}")
            }
        })
    }

    private fun formatearFecha(fechaIso: String): String {
        return try {
            val sdf    = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val date   = sdf.parse(fechaIso) ?: return fechaIso
            "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)} HRS"
        } catch (e: Exception) { fechaIso }
    }

    // ── GRAVEDAD ACTUALIZACIÓN ────────────────────────────────────
    private fun setupGravedadActualizacion() {
        resaltarGravedadActualizacion(pA.btnGravedadNormal)
        pA.btnGravedadNormal.setOnClickListener     { gravedadActualizacion = "NORMAL";     resaltarGravedadActualizacion(pA.btnGravedadNormal) }
        pA.btnGravedadPrecaucion.setOnClickListener { gravedadActualizacion = "PRECAUCION"; resaltarGravedadActualizacion(pA.btnGravedadPrecaucion) }
        pA.btnGravedadCritico.setOnClickListener    { gravedadActualizacion = "CRITICO";    resaltarGravedadActualizacion(pA.btnGravedadCritico) }
    }

    private fun resaltarGravedadActualizacion(sel: LinearLayout) {
        listOf(pA.btnGravedadNormal, pA.btnGravedadPrecaucion, pA.btnGravedadCritico)
            .forEach { it.alpha = if (it == sel) 1f else 0.35f }
    }

    private fun resaltarGravedadDesdeRobot(nivel: String) {
        val boton = when (nivel.uppercase()) {
            "CRITICO", "EMERGENCIA" -> pA.btnGravedadCritico
            "ADVERTENCIA"           -> pA.btnGravedadPrecaucion
            else                    -> pA.btnGravedadNormal
        }
        gravedadActualizacion = when (nivel.uppercase()) {
            "CRITICO", "EMERGENCIA" -> "CRITICO"
            "ADVERTENCIA"           -> "PRECAUCION"
            else                    -> "NORMAL"
        }
        resaltarGravedadActualizacion(boton)
    }

    // ── GRAVEDAD FINALIZAR ────────────────────────────────────────
    private fun setupGravedadFinalizar() {
        actualizarBotonesGravedadFinal("CRITICO")
        pF.btnNormal.setOnClickListener     { gravedadFinal = "NORMAL";     actualizarBotonesGravedadFinal("NORMAL") }
        pF.btnPrecaucion.setOnClickListener { gravedadFinal = "PRECAUCION"; actualizarBotonesGravedadFinal("PRECAUCION") }
        pF.btnCritico.setOnClickListener    { gravedadFinal = "CRITICO";    actualizarBotonesGravedadFinal("CRITICO") }
    }

    private fun actualizarBotonesGravedadFinal(sel: String) {
        val inactivo = ColorStateList.valueOf(color(R.color.ares_border))
        listOf(pF.btnNormal, pF.btnPrecaucion, pF.btnCritico).forEach {
            it.backgroundTintList = inactivo
            it.setTextColor(color(R.color.ares_grey))
        }
        when (sel) {
            "NORMAL"     -> { pF.btnNormal.backgroundTintList     = ColorStateList.valueOf(color(R.color.ares_green));        pF.btnNormal.setTextColor(color(R.color.ares_black)) }
            "PRECAUCION" -> { pF.btnPrecaucion.backgroundTintList = ColorStateList.valueOf(color(R.color.ares_yellow));       pF.btnPrecaucion.setTextColor(color(R.color.ares_black)) }
            "CRITICO"    -> { pF.btnCritico.backgroundTintList    = ColorStateList.valueOf(color(R.color.ares_orange));  pF.btnCritico.setTextColor(color(R.color.ares_white)) }
        }
    }

    // ── CONTADORES ACTUALIZACIÓN ──────────────────────────────────
    private fun setupContadoresActualizacion() {
        refrescarActHeridas(); refrescarActFallecidas(); refrescarActRescatadas()
        pA.btnActHeridosPlus.setOnClickListener     { actHeridas++;    refrescarActHeridas() }
        pA.btnActHeridosMinus.setOnClickListener    { if (actHeridas > 0)    { actHeridas--;    refrescarActHeridas() } }
        pA.btnActFallecidasPlus.setOnClickListener  { actFallecidas++; refrescarActFallecidas() }
        pA.btnActFallecidasMinus.setOnClickListener { if (actFallecidas > 0) { actFallecidas--; refrescarActFallecidas() } }
        pA.btnActRescatadasPlus.setOnClickListener  { actRescatadas++; refrescarActRescatadas() }
        pA.btnActRescatadasMinus.setOnClickListener { if (actRescatadas > 0) { actRescatadas--; refrescarActRescatadas() } }
    }

    private fun refrescarActHeridas()    { pA.tvActHeridosCount.text    = actHeridas.toString();    pA.tvActHeridosCount.setTextColor(color(if (actHeridas > 0)    R.color.ares_blue_report else R.color.ares_grey)) }
    private fun refrescarActFallecidas() { pA.tvActFallecidasCount.text = actFallecidas.toString(); pA.tvActFallecidasCount.setTextColor(color(if (actFallecidas > 0) R.color.ares_red         else R.color.ares_grey)) }
    private fun refrescarActRescatadas() { pA.tvActRescatadasCount.text = actRescatadas.toString(); pA.tvActRescatadasCount.setTextColor(color(if (actRescatadas > 0) R.color.ares_green       else R.color.ares_grey)) }

    // ── CONTADORES FINALIZAR ──────────────────────────────────────
    private fun setupContadoresFinal() {
        refrescarFinHeridas(); refrescarFinFallecidas(); refrescarFinRescatadas(); refrescarFinSinConfirmar()
        pF.btnHeridosPlus.setOnClickListener       { finHeridas++;      refrescarFinHeridas() }
        pF.btnHeridosMinus.setOnClickListener      { if (finHeridas > 0)      { finHeridas--;      refrescarFinHeridas() } }
        pF.btnFallecidasPlus.setOnClickListener    { finFallecidas++;   refrescarFinFallecidas() }
        pF.btnFallecidasMinus.setOnClickListener   { if (finFallecidas > 0)   { finFallecidas--;   refrescarFinFallecidas() } }
        pF.btnRescatadasPlus.setOnClickListener    { finRescatadas++;   refrescarFinRescatadas() }
        pF.btnRescatadasMinus.setOnClickListener   { if (finRescatadas > 0)   { finRescatadas--;   refrescarFinRescatadas() } }
        pF.btnSinConfirmarPlus.setOnClickListener  { finSinConfirmar++; refrescarFinSinConfirmar() }
        pF.btnSinConfirmarMinus.setOnClickListener { if (finSinConfirmar > 0) { finSinConfirmar--; refrescarFinSinConfirmar() } }
    }

    private fun refrescarFinHeridas()      { pF.tvHeridosCount.text      = finHeridas.toString();      pF.tvHeridosCount.setTextColor(color(if (finHeridas > 0)      R.color.ares_blue_report else R.color.ares_grey)) }
    private fun refrescarFinFallecidas()   { pF.tvFallecidasCount.text   = finFallecidas.toString();   pF.tvFallecidasCount.setTextColor(color(if (finFallecidas > 0)   R.color.ares_red         else R.color.ares_grey)) }
    private fun refrescarFinRescatadas()   { pF.tvRescatadasCount.text   = finRescatadas.toString();   pF.tvRescatadasCount.setTextColor(color(if (finRescatadas > 0)   R.color.ares_green       else R.color.ares_grey)) }
    private fun refrescarFinSinConfirmar() { pF.tvSinConfirmarCount.text = finSinConfirmar.toString(); pF.tvSinConfirmarCount.setTextColor(color(if (finSinConfirmar > 0) R.color.ares_yellow      else R.color.ares_grey)) }

    // ── DURACIÓN ──────────────────────────────────────────────────
    private fun actualizarDuracionMision() {
        val minutos = ((System.currentTimeMillis() - horaInicioMision) / 60000).toInt()
        pF.tvDuracionMision.text = "$minutos min"
    }

    // ── GALERÍA ───────────────────────────────────────────────────
    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        galeriaLauncher.launch(intent)
    }

    private fun mostrarFotoSeleccionada(uri: Uri) {
        pF.btnSubirFoto.removeAllViews()
        val imageView = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            scaleType        = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setImageURI(uri)
        }
        val overlay = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            orientation  = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setBackgroundColor(color(R.color.ares_bg_dark))
        }
        val tvNombre = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text      = obtenerNombreArchivo(uri)
            setTextColor(color(R.color.ares_white))
            textSize  = 11f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val tvCambiar = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "✎ CAMBIAR"; setTextColor(color(R.color.ares_blue_report)); textSize = 11f
            setPadding(dpToPx(8), 0, 0, 0)
            setOnClickListener { abrirGaleria() }
        }
        overlay.addView(tvNombre); overlay.addView(tvCambiar)
        val frame = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(imageView)
        val overlayParams = android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM)
        frame.addView(overlay, overlayParams)
        pF.btnSubirFoto.addView(frame)
        pF.btnSubirFoto.isClickable = false
    }

    private fun obtenerNombreArchivo(uri: Uri): String {
        var nombre = "foto_evidencia.jpg"
        try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) nombre = cursor.getString(idx)
                }
            }
        } catch (e: Exception) { Log.e("GALERIA", "${e.message}") }
        return nombre
    }

    // ── BITÁCORA ──────────────────────────────────────────────────
    private fun renderizarBitacora() {
        pF.contenedorBitacora.removeAllViews()

        if (bitacoraEntradas.isEmpty()) {
            pF.contenedorBitacora.addView(TextView(requireContext()).apply {
                text = "Sin entradas en la bitácora"
                setTextColor(color(R.color.ares_grey))
                textSize = 13f
                gravity  = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            return
        }

        bitacoraEntradas.forEachIndexed { index, entrada ->
            val fila = LinearLayout(requireContext()).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val dot = View(requireContext()).apply {
                val p = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10))
                p.marginEnd = dpToPx(12); p.topMargin = dpToPx(4); layoutParams = p
                setBackgroundResource(when (entrada.tipo) {
                    "INCIDENTE", "DECISION" -> R.drawable.bg_dot_yellow
                    else                    -> R.drawable.bg_dot_active
                })
            }
            val columna = LinearLayout(requireContext()).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvHora = TextView(requireContext()).apply {
                text     = entrada.hora; textSize = 9f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(color(when (entrada.tipo) {
                    "INCIDENTE" -> R.color.ares_yellow
                    else        -> R.color.ares_blue_report
                }))
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.bottomMargin = dpToPx(4); layoutParams = p
            }
            val tvTexto = TextView(requireContext()).apply {
                text     = entrada.texto; textSize = 13f
                setTextColor(color(R.color.ares_white))
                setLineSpacing(dpToPx(2).toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            columna.addView(tvHora); columna.addView(tvTexto)
            fila.addView(dot); fila.addView(columna)
            pF.contenedorBitacora.addView(fila)

            if (index < bitacoraEntradas.size - 1) {
                pF.contenedorBitacora.addView(View(requireContext()).apply {
                    val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1))
                    p.topMargin = dpToPx(14); p.bottomMargin = dpToPx(14); layoutParams = p
                    setBackgroundColor(color(R.color.ares_border))
                })
            }
        }
    }

    private fun agregarEntradaBitacoraLocal(texto: String, tipo: String = "NOTA") {
        val hora = "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())} HRS"
        bitacoraEntradas.add(EntradaBitacora(hora, texto, tipo))
        renderizarBitacora()
    }

    private fun agregarEntradaBitacora(texto: String, tipo: String = "NOTA") {
        agregarEntradaBitacoraLocal(texto, tipo)
        // Guardar en Django via Retrofit
        api.postBitacora(
            BitacoraRequest(
                mision       = misionId,
                usuario      = autorId,
                tipo_entrada = tipo,
                contenido    = texto,
            )
        ).enqueue(object : Callback<BitacoraDto> {
            override fun onResponse(call: Call<BitacoraDto>, response: Response<BitacoraDto>) {
                Log.d("API", "Bitácora guardada: ${response.code()}")
            }
            override fun onFailure(call: Call<BitacoraDto>, t: Throwable) {
                Log.e("API", "Error bitácora: ${t.message}")
            }
        })
    }

    // ── BOTONES PRINCIPALES ───────────────────────────────────────
    private fun setupBotones() {
        pA.btnEnviarActualizacion.setOnClickListener { enviarReporteActualizacion() }
        pF.btnEnviarBase.setOnClickListener          { enviarReporteFinal() }
        pF.btnSubirFoto.setOnClickListener           { abrirGaleria() }
        binding.btnBack.setOnClickListener           { requireActivity().onBackPressedDispatcher.onBackPressed() }
    }

    // ── API: ReporteActualizacion (Retrofit POST) ─────────────────
    private fun enviarReporteActualizacion() {
        val resumen = pA.etNotasActualizacion.text.toString().trim()
        val accion  = pA.etAccionRecomendada.text.toString().trim()

        val request = ReporteActualizacionRequest(
            mision              = misionId,
            autor               = autorId,
            nivel_riesgo        = gravedadActualizacion,
            resumen             = resumen.ifEmpty { "Sin notas" },
            victimas_heridas    = actHeridas,
            victimas_fallecidas = actFallecidas,
            victimas_rescatadas = actRescatadas,
            accion_recomendada  = accion,
        )

        api.postReporteActualizacion(request).enqueue(object : Callback<ReporteActualizacionDto> {
            override fun onResponse(call: Call<ReporteActualizacionDto>, response: Response<ReporteActualizacionDto>) {
                if (!isAdded) return
                if (response.isSuccessful) {
                    val tipo  = when (gravedadActualizacion) { "CRITICO" -> "INCIDENTE"; "PRECAUCION" -> "EVENTO"; else -> "NOTA" }
                    val texto = resumen.ifEmpty { "Actualización [$gravedadActualizacion]" }
                    agregarEntradaBitacoraLocal("$texto | H:$actHeridas F:$actFallecidas R:$actRescatadas", tipo)
                    pA.etNotasActualizacion.text?.clear()
                    pA.etAccionRecomendada.text?.clear()
                    Toast.makeText(requireContext(), "✅ Actualización enviada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<ReporteActualizacionDto>, t: Throwable) {
                if (!isAdded) return
                Toast.makeText(requireContext(), "Sin conexión: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    // ── API: Foto (Retrofit Multipart) ────────────────────────────
    private fun subirFoto(uri: Uri) {
        try {
            val contentResolver = requireContext().contentResolver
            val inputStream     = contentResolver.openInputStream(uri) ?: return
            val tempFile        = File.createTempFile("foto_", ".jpg", requireContext().cacheDir)
            tempFile.outputStream().use { inputStream.copyTo(it) }

            val misionBody  = misionId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val usuarioBody = autorId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val tipoBody    = "FOTO".toRequestBody("text/plain".toMediaTypeOrNull())
            val archivoPart = MultipartBody.Part.createFormData(
                "archivo", tempFile.name,
                tempFile.asRequestBody("image/*".toMediaTypeOrNull())
            )

            api.postEvidencia(misionBody, usuarioBody, tipoBody, archivoPart)
                .enqueue(object : Callback<EvidenciaDto> {
                    override fun onResponse(call: Call<EvidenciaDto>, response: Response<EvidenciaDto>) {
                        Log.d("API", "Foto subida: ${response.code()}")
                    }
                    override fun onFailure(call: Call<EvidenciaDto>, t: Throwable) {
                        Log.e("API", "Error foto: ${t.message}")
                    }
                })
        } catch (e: Exception) {
            Log.e("FOTO", "Error: ${e.message}")
        }
    }

    // ── API: ReporteFinal (Retrofit POST) ─────────────────────────
    private fun enviarReporteFinal() {
        val duracion = ((System.currentTimeMillis() - horaInicioMision) / 60000).toInt()

        // Subir foto si hay una seleccionada
        fotoUri?.let { subirFoto(it) }

        val request = ReporteFinalRequest(
            mision                 = misionId,
            generado_por           = autorId,
            victimas_heridas       = finHeridas,
            victimas_fallecidas    = finFallecidas,
            victimas_rescatadas    = finRescatadas,
            victimas_sin_confirmar = finSinConfirmar,
            nivel_riesgo_maximo    = gravedadFinal,
            duracion_minutos       = duracion,
        )

        api.postReporteFinal(request).enqueue(object : Callback<ReporteFinalDto> {
            override fun onResponse(call: Call<ReporteFinalDto>, response: Response<ReporteFinalDto>) {
                if (!isAdded) return
                if (response.isSuccessful) {
                    agregarEntradaBitacoraLocal(
                        "MISIÓN FINALIZADA — Gravedad: $gravedadFinal | " +
                                "H:$finHeridas F:$finFallecidas R:$finRescatadas SC:$finSinConfirmar | ${duracion}min",
                        "DECISION"
                    )
                    pF.etNotasOperador.text?.clear()
                    wsClient?.close()
                    cerrarMisionEnDjango()
                    Toast.makeText(requireContext(), "🚨 Misión finalizada y enviada a base", Toast.LENGTH_LONG).show()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("API", "Error reporte final ${response.code()}: $errorBody")
                    Toast.makeText(requireContext(), "Error: ${response.code()} - $errorBody", Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<ReporteFinalDto>, t: Throwable) {
                if (!isAdded) return
                Toast.makeText(requireContext(), "Sin conexión: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    // ── Cerrar misión en Django ───────────────────────────────────────
    private fun cerrarMisionEnDjango() {
        api.cerrarMision(misionId).enqueue(object : Callback<MisionCerradaDto> {
            override fun onResponse(
                call: Call<MisionCerradaDto>,
                response: Response<MisionCerradaDto>
            ) {
                Log.d("API", "Misión cerrada: ${response.code()}")
            }
            override fun onFailure(call: Call<MisionCerradaDto>, t: Throwable) {
                Log.e("API", "Error cerrando misión: ${t.message}")
            }
        })
    }

    // ── HORA ──────────────────────────────────────────────────────
    private fun actualizarHora() {
        pA.tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isAdded) actualizarHora()
        }, 60_000)
    }

    // ── UTILIDADES ────────────────────────────────────────────────
    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        wsClient?.close()
        _binding = null
    }
}