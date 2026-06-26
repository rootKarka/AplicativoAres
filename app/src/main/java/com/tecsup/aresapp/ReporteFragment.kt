package com.tecsup.aresapp

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
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
import androidx.fragment.app.Fragment
import com.tecsup.aresapp.databinding.FragmentReporteBinding
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class ReporteFragment : Fragment() {

    private var _binding: FragmentReporteBinding? = null
    private val binding get() = _binding!!

    // Accesos directos a cada panel (evita repetir binding.panelX. en todo el código)
    private val pA get() = binding.panelActualizacion   // Panel Actualización
    private val pF get() = binding.panelFinalizar        // Panel Finalizar

    // ── Config ───────────────────────────────────────────────────────────────
    private val BASE_URL = "http://10.0.2.2:8000/api"
    private var misionId = 1
    private var autorId  = 1

    // ── Hora inicio para duracion_minutos ─────────────────────────────────────
    private val horaInicioMision = System.currentTimeMillis()

    // ── Foto seleccionada ─────────────────────────────────────────────────────
    private var fotoUri: Uri? = null

    // ── Lanzador de galería ───────────────────────────────────────────────────
    private val galeriaLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                fotoUri = uri
                mostrarFotoSeleccionada(uri)
            }
        }
    }

    // ── Contadores Actualización ──────────────────────────────────────────────
    private var actHeridas    = 0
    private var actFallecidas = 0
    private var actRescatadas = 0

    // ── Contadores Finalizar ──────────────────────────────────────────────────
    private var finHeridas      = 0
    private var finFallecidas   = 0
    private var finRescatadas   = 0
    private var finSinConfirmar = 0

    // ── Gravedad ──────────────────────────────────────────────────────────────
    // Valores exactos de NIVEL_CHOICES Django: NORMAL | PRECAUCION | ALTO_RIESGO | CRITICO
    private var gravedadActualizacion = "NORMAL"
    private var gravedadFinal         = "CRITICO"

    // ── Bitácora ──────────────────────────────────────────────────────────────
    // tipo coincide con TIPO_CHOICES Django: NOTA | EVENTO | DECISION | HALLAZGO | INCIDENTE
    data class EntradaBitacora(val hora: String, val texto: String, val tipo: String)

    private val bitacoraEntradas = mutableListOf(
        EntradaBitacora("14:00 HRS", "Humo denso en sector B-4, avanzando lento por escombros.", "EVENTO"),
        EntradaBitacora("15:30 HRS", "Nivel crítico de gas detectado, víctima localizada mediante sensor térmico.", "INCIDENTE")
    )

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReporteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupGravedadActualizacion()
        setupGravedadFinalizar()
        setupContadoresActualizacion()
        setupContadoresFinal()
        setupBotones()
        actualizarHora()
        renderizarBitacora()
    }

    // ── TABS ──────────────────────────────────────────────────────────────────
    private fun setupTabs() {
        setTabActualizacionActivo(true)
        binding.tabActualizacion.setOnClickListener { setTabActualizacionActivo(true) }
        binding.tabFinalizar.setOnClickListener     { setTabActualizacionActivo(false) }
    }

    private fun setTabActualizacionActivo(activo: Boolean) {
        if (activo) {
            binding.tabActualizacion.setBackgroundResource(R.drawable.bg_tab_orange)
            binding.tvTabActualizacion.setTextColor(0xFFE8E8E8.toInt())
            binding.tabFinalizar.setBackgroundResource(android.R.color.transparent)
            binding.tvTabFinalizar.setTextColor(0xFF6B7280.toInt())
            pA.layoutContenidoActualizacion.visibility = View.VISIBLE
            pF.layoutContenidoFinalizar.visibility     = View.GONE
        } else {
            binding.tabFinalizar.setBackgroundResource(R.drawable.bg_tab_orange)
            binding.tvTabFinalizar.setTextColor(0xFFE8E8E8.toInt())
            binding.tabActualizacion.setBackgroundResource(android.R.color.transparent)
            binding.tvTabActualizacion.setTextColor(0xFF6B7280.toInt())
            pF.layoutContenidoFinalizar.visibility     = View.VISIBLE
            pA.layoutContenidoActualizacion.visibility = View.GONE
            actualizarDuracionMision()
        }
    }

    // ── GRAVEDAD ACTUALIZACIÓN ────────────────────────────────────────────────
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

    // ── GRAVEDAD FINALIZAR ────────────────────────────────────────────────────
    private fun setupGravedadFinalizar() {
        actualizarBotonesGravedadFinal("CRITICO")
        pF.btnNormal.setOnClickListener     { gravedadFinal = "NORMAL";     actualizarBotonesGravedadFinal("NORMAL") }
        pF.btnPrecaucion.setOnClickListener { gravedadFinal = "PRECAUCION"; actualizarBotonesGravedadFinal("PRECAUCION") }
        pF.btnCritico.setOnClickListener    { gravedadFinal = "CRITICO";    actualizarBotonesGravedadFinal("CRITICO") }
    }

    private fun actualizarBotonesGravedadFinal(sel: String) {
        val inactivo = ColorStateList.valueOf(0xFF2C2F3B.toInt())
        listOf(pF.btnNormal, pF.btnPrecaucion, pF.btnCritico).forEach {
            it.backgroundTintList = inactivo
            it.setTextColor(0xFF6B7280.toInt())
        }
        when (sel) {
            "NORMAL"     -> { pF.btnNormal.backgroundTintList     = ColorStateList.valueOf(0xFF4CAF50.toInt()); pF.btnNormal.setTextColor(0xFFE8E8E8.toInt()) }
            "PRECAUCION" -> { pF.btnPrecaucion.backgroundTintList = ColorStateList.valueOf(0xFFFFC107.toInt()); pF.btnPrecaucion.setTextColor(0xFF0A0A0A.toInt()) }
            "CRITICO"    -> { pF.btnCritico.backgroundTintList    = ColorStateList.valueOf(0xFF1F70C1.toInt()); pF.btnCritico.setTextColor(0xFFE8E8E8.toInt()) }
        }
    }

    // ── CONTADORES ACTUALIZACIÓN ──────────────────────────────────────────────
    private fun setupContadoresActualizacion() {
        refrescarActHeridas(); refrescarActFallecidas(); refrescarActRescatadas()

        pA.btnActHeridosPlus.setOnClickListener     { actHeridas++;    refrescarActHeridas() }
        pA.btnActHeridosMinus.setOnClickListener    { if (actHeridas > 0)    { actHeridas--;    refrescarActHeridas() } }
        pA.btnActFallecidasPlus.setOnClickListener  { actFallecidas++; refrescarActFallecidas() }
        pA.btnActFallecidasMinus.setOnClickListener { if (actFallecidas > 0) { actFallecidas--; refrescarActFallecidas() } }
        pA.btnActRescatadasPlus.setOnClickListener  { actRescatadas++; refrescarActRescatadas() }
        pA.btnActRescatadasMinus.setOnClickListener { if (actRescatadas > 0) { actRescatadas--; refrescarActRescatadas() } }
    }

    private fun refrescarActHeridas()    { pA.tvActHeridosCount.text    = actHeridas.toString();    pA.tvActHeridosCount.setTextColor(if (actHeridas > 0)    0xFF1F70C1.toInt() else 0xFF6B7280.toInt()) }
    private fun refrescarActFallecidas() { pA.tvActFallecidasCount.text = actFallecidas.toString(); pA.tvActFallecidasCount.setTextColor(if (actFallecidas > 0) 0xFFF44336.toInt() else 0xFF6B7280.toInt()) }
    private fun refrescarActRescatadas() { pA.tvActRescatadasCount.text = actRescatadas.toString(); pA.tvActRescatadasCount.setTextColor(if (actRescatadas > 0) 0xFF4CAF50.toInt() else 0xFF6B7280.toInt()) }

    // ── CONTADORES FINALIZAR ──────────────────────────────────────────────────
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

    private fun refrescarFinHeridas()      { pF.tvHeridosCount.text      = finHeridas.toString();      pF.tvHeridosCount.setTextColor(if (finHeridas > 0)      0xFF1F70C1.toInt() else 0xFF6B7280.toInt()) }
    private fun refrescarFinFallecidas()   { pF.tvFallecidasCount.text   = finFallecidas.toString();   pF.tvFallecidasCount.setTextColor(if (finFallecidas > 0)   0xFFF44336.toInt() else 0xFF6B7280.toInt()) }
    private fun refrescarFinRescatadas()   { pF.tvRescatadasCount.text   = finRescatadas.toString();   pF.tvRescatadasCount.setTextColor(if (finRescatadas > 0)   0xFF4CAF50.toInt() else 0xFF6B7280.toInt()) }
    private fun refrescarFinSinConfirmar() { pF.tvSinConfirmarCount.text = finSinConfirmar.toString(); pF.tvSinConfirmarCount.setTextColor(if (finSinConfirmar > 0) 0xFFFFC107.toInt() else 0xFF6B7280.toInt()) }

    // ── DURACIÓN ──────────────────────────────────────────────────────────────
    private fun actualizarDuracionMision() {
        val minutos = ((System.currentTimeMillis() - horaInicioMision) / 60000).toInt()
        pF.tvDuracionMision.text = "$minutos min"
    }

    // ── GALERÍA ───────────────────────────────────────────────────────────────
    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        galeriaLauncher.launch(intent)
    }

    private fun mostrarFotoSeleccionada(uri: Uri) {
        pF.btnSubirFoto.removeAllViews()

        val imageView = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setImageURI(uri)
        }

        val overlay = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setBackgroundColor(0xCC0A0A0A.toInt())
        }

        val tvNombreFoto = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = obtenerNombreArchivo(uri)
            setTextColor(0xFFE8E8E8.toInt())
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val tvCambiar = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "✎ CAMBIAR"
            setTextColor(0xFF1F70C1.toInt())
            textSize = 11f
            setPadding(dpToPx(8), 0, 0, 0)
            setOnClickListener { abrirGaleria() }
        }

        overlay.addView(tvNombreFoto)
        overlay.addView(tvCambiar)

        val frame = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        frame.addView(imageView)

        val overlayParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM
        )
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
        } catch (e: Exception) {
            Log.e("GALERIA", "No se pudo obtener nombre: ${e.message}")
        }
        return nombre
    }

    // ── BITÁCORA ──────────────────────────────────────────────────────────────
    private fun renderizarBitacora() {
        pF.contenedorBitacora.removeAllViews()

        bitacoraEntradas.forEachIndexed { index, entrada ->

            val fila = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val dot = View(requireContext()).apply {
                val p = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10))
                p.marginEnd = dpToPx(12)
                p.topMargin = dpToPx(4)
                layoutParams = p
                setBackgroundResource(
                    when (entrada.tipo) {
                        "INCIDENTE", "DECISION" -> R.drawable.bg_dot_yellow
                        else                    -> R.drawable.bg_dot_active
                    }
                )
            }

            val columna = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvHora = TextView(requireContext()).apply {
                text = entrada.hora
                textSize = 9f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(
                    when (entrada.tipo) {
                        "INCIDENTE" -> 0xFFFFC107.toInt()
                        "DECISION"  -> 0xFF1F70C1.toInt()
                        else        -> 0xFF1F70C1.toInt()
                    }
                )
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                p.bottomMargin = dpToPx(4)
                layoutParams = p
            }

            val tvTexto = TextView(requireContext()).apply {
                text = entrada.texto
                textSize = 13f
                setTextColor(0xFFE8E8E8.toInt())
                setLineSpacing(dpToPx(2).toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            columna.addView(tvHora)
            columna.addView(tvTexto)
            fila.addView(dot)
            fila.addView(columna)
            pF.contenedorBitacora.addView(fila)

            if (index < bitacoraEntradas.size - 1) {
                val sep = View(requireContext()).apply {
                    val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1))
                    p.topMargin    = dpToPx(14)
                    p.bottomMargin = dpToPx(14)
                    layoutParams   = p
                    setBackgroundColor(0xFF2C2F3B.toInt())
                }
                pF.contenedorBitacora.addView(sep)
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
        enviarBitacoraADjango(texto, tipo)
    }

    // ── BOTONES PRINCIPALES ───────────────────────────────────────────────────
    private fun setupBotones() {

        pA.btnEnviarActualizacion.setOnClickListener {
            val resumen = pA.etNotasActualizacion.text.toString().trim()
            if (resumen.isEmpty()) {
                Toast.makeText(requireContext(), "Escribe al menos una nota antes de enviar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            enviarReporteActualizacion(resumen)
        }

        pF.btnEnviarBase.setOnClickListener {
            val notas = pF.etNotasOperador.text.toString().trim()
            enviarReporteFinal(notas)
        }

        pF.btnSubirFoto.setOnClickListener {
            abrirGaleria()
        }

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    // ── API: ReporteActualizacion ─────────────────────────────────────────────
    private fun enviarReporteActualizacion(resumen: String) {
        val accion = pA.etAccionRecomendada.text.toString().trim()

        val body = JSONObject().apply {
            put("mision",              misionId)
            put("autor",               autorId)
            put("nivel_riesgo",        gravedadActualizacion)  // NORMAL|PRECAUCION|ALTO_RIESGO|CRITICO
            put("resumen",             resumen)
            put("victimas_heridas",    actHeridas)
            put("victimas_fallecidas", actFallecidas)
            put("victimas_rescatadas", actRescatadas)
            put("accion_recomendada",  accion)
        }

        postADjango("$BASE_URL/reportes/actualizacion/", body,
            onExito = {
                val tipo = when (gravedadActualizacion) {
                    "CRITICO"    -> "INCIDENTE"
                    "PRECAUCION" -> "EVENTO"
                    else         -> "NOTA"
                }
                agregarEntradaBitacoraLocal("Actualización [$gravedadActualizacion]: $resumen", tipo)
                pA.etNotasActualizacion.text?.clear()
                pA.etAccionRecomendada.text?.clear()
                Toast.makeText(requireContext(), "✅ Actualización enviada", Toast.LENGTH_SHORT).show()
            },
            onError = { code ->
                Toast.makeText(requireContext(), "Error del servidor: $code", Toast.LENGTH_LONG).show()
            }
        )
    }

    // ── API: ReporteFinal ─────────────────────────────────────────────────────
    private fun enviarReporteFinal(notas: String) {
        val duracion = ((System.currentTimeMillis() - horaInicioMision) / 60000).toInt()

        val body = JSONObject().apply {
            put("mision",                 misionId)
            put("generado_por",           autorId)
            put("victimas_heridas",       finHeridas)
            put("victimas_fallecidas",    finFallecidas)
            put("victimas_rescatadas",    finRescatadas)
            put("victimas_sin_confirmar", finSinConfirmar)
            put("nivel_riesgo_maximo",    gravedadFinal)
            put("duracion_minutos",       duracion)
        }

        postADjango("$BASE_URL/reportes/final/", body,
            onExito = {
                agregarEntradaBitacoraLocal(
                    "MISIÓN FINALIZADA — Gravedad: $gravedadFinal | H:$finHeridas F:$finFallecidas R:$finRescatadas SC:$finSinConfirmar | $duracion min",
                    "DECISION"
                )
                pF.etNotasOperador.text?.clear()
                Toast.makeText(requireContext(), "🚨 Misión finalizada y enviada a base", Toast.LENGTH_LONG).show()
            },
            onError = { code ->
                Toast.makeText(requireContext(), "Error del servidor: $code", Toast.LENGTH_LONG).show()
            }
        )
    }

    // ── API: Bitácora ─────────────────────────────────────────────────────────
    private fun enviarBitacoraADjango(contenido: String, tipo: String) {
        val body = JSONObject().apply {
            put("mision",       misionId)
            put("usuario",      autorId)
            put("tipo_entrada", tipo)       // NOTA|EVENTO|DECISION|HALLAZGO|INCIDENTE
            put("contenido",    contenido)
            put("es_voz",       false)
            put("latitud",      0)
            put("longitud",     0)
        }

        postADjango("$BASE_URL/bitacora/", body,
            onExito = { Log.d("API_BITACORA", "Guardada: $tipo") },
            onError = { code -> Log.e("API_BITACORA", "Error $code") }
        )
    }

    // ── HTTP helper ───────────────────────────────────────────────────────────
    private fun postADjango(
        url: String,
        body: JSONObject,
        onExito: () -> Unit,
        onError: (Int) -> Unit
    ) {
        Thread {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod  = "POST"
                    doOutput       = true
                    connectTimeout = 5000
                    readTimeout    = 5000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept",       "application/json")
                    // Si usas JWT: setRequestProperty("Authorization", "Bearer $token")
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                val code = conn.responseCode
                Log.d("API_POST", "[$code] $url")
                requireActivity().runOnUiThread {
                    if (code in 200..201) onExito() else onError(code)
                }
            } catch (e: Exception) {
                Log.e("API_POST", "Error: ${e.message}")
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Sin conexión con el servidor", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ── HORA ──────────────────────────────────────────────────────────────────
    private fun actualizarHora() {
        pA.tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}