package com.tecsup.aresapp.feature.reporte

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tecsup.aresapp.R
import com.tecsup.aresapp.databinding.FragmentReporteBinding

class ReporteFragment : Fragment() {

    // ── ViewBinding ─────────────────────────────────────────────
    private var _binding: FragmentReporteBinding? = null
    private val binding get() = _binding!!

    // ── Estado ───────────────────────────────────────────────────
    private var heridosCount = 1
    private var inconscientesCount = 0
    private var gravedadActual = Gravedad.CRITICO

    private enum class Gravedad { NORMAL, PRECAUCION, CRITICO }

    // ───────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReporteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGravedadButtons()
        setupCounters()
        setupGaleria()
        setupEnviarButton()
        buildMiniChart()
        updateGravedadUI()
    }

    // ── Gravedad ─────────────────────────────────────────────────
    private fun setupGravedadButtons() {
        binding.btnNormal.setOnClickListener {
            gravedadActual = Gravedad.NORMAL
            updateGravedadUI()
        }
        binding.btnPrecaucion.setOnClickListener {
            gravedadActual = Gravedad.PRECAUCION
            updateGravedadUI()
        }
        binding.btnCritico.setOnClickListener {
            gravedadActual = Gravedad.CRITICO
            updateGravedadUI()
        }
    }

    private fun updateGravedadUI() {
        val ctx = requireContext()

        // Reset todos a inactivo
        listOf(binding.btnNormal, binding.btnPrecaucion, binding.btnCritico).forEach { btn ->
            btn.setBackgroundResource(R.drawable.bg_btn_gravedad_normal)
            btn.setTextColor(ctx.getColor(R.color.ares_grey))
        }

        // Activar el seleccionado
        val (activeBtn, activeColor) = when (gravedadActual) {
            Gravedad.NORMAL    -> binding.btnNormal    to ctx.getColor(R.color.ares_green)
            Gravedad.PRECAUCION -> binding.btnPrecaucion to ctx.getColor(R.color.ares_yellow)
            Gravedad.CRITICO   -> binding.btnCritico   to ctx.getColor(R.color.ares_orange)
        }
        activeBtn.setBackgroundColor(activeColor)
        activeBtn.setTextColor(ctx.getColor(R.color.ares_dark))
    }

    // ── Contadores ───────────────────────────────────────────────
    private fun setupCounters() {
        // Heridos
        binding.btnHeridosMinus.setOnClickListener {
            if (heridosCount > 0) heridosCount--
            updateCounter(binding.tvHeridosCount, heridosCount)
        }
        binding.btnHeridosPlus.setOnClickListener {
            heridosCount++
            updateCounter(binding.tvHeridosCount, heridosCount)
        }

        // Inconscientes
        binding.btnInconscientesMinus.setOnClickListener {
            if (inconscientesCount > 0) inconscientesCount--
            updateCounter(binding.tvInconscientesCount, inconscientesCount)
        }
        binding.btnInconscientesPlus.setOnClickListener {
            inconscientesCount++
            updateCounter(binding.tvInconscientesCount, inconscientesCount)
        }

        // Valores iniciales
        updateCounter(binding.tvHeridosCount, heridosCount)
        updateCounter(binding.tvInconscientesCount, inconscientesCount)
    }

    private fun updateCounter(tv: TextView, value: Int) {
        tv.text = value.toString()
        tv.setTextColor(
            requireContext().getColor(
                if (value > 0) R.color.ares_orange else R.color.ares_grey
            )
        )
    }

    // ── Galería ──────────────────────────────────────────────────
    private fun setupGaleria() {
        binding.btnSubirFoto.setOnClickListener {
            // TODO: reemplazar con Intent a galería o cámara
            // val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            // startActivityForResult(intent, REQUEST_IMAGE_PICK)
            Toast.makeText(requireContext(), "Seleccionar foto del dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Enviar ───────────────────────────────────────────────────
    private fun setupEnviarButton() {
        binding.btnEnviarBase.setOnClickListener {
            val reporte = ReporteData(
                mision        = "ARES-01",
                tipoDocumento = "Reporte de Situación",
                gravedad      = gravedadActual.name,
                heridos       = heridosCount,
                inconscientes = inconscientesCount,
                notas         = binding.etNotas.text.toString().trim()
            )
            enviarReporte(reporte)
        }
    }

    private fun enviarReporte(reporte: ReporteData) {
        // TODO: conectar a ViewModel / WebSocket / API
        val msg = "✓ Reporte enviado | Gravedad: ${reporte.gravedad} | " +
                "Heridos: ${reporte.heridos} | Inconscientes: ${reporte.inconscientes}"
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    // ── Mini bar chart decorativo ────────────────────────────────
    private fun buildMiniChart() {
        val bars = listOf(10, 18, 12, 22, 30, 16, 28, 14)
        val density = resources.displayMetrics.density

        bars.forEach { heightDp ->
            val bar = View(requireContext())
            val params = LinearLayout.LayoutParams(
                (6 * density).toInt(),
                (heightDp * density).toInt()
            )
            params.marginEnd = (3 * density).toInt()
            bar.layoutParams = params
            bar.setBackgroundColor(
                requireContext().getColor(
                    if (heightDp >= 28) R.color.ares_orange else R.color.ares_border
                )
            )
            binding.miniChart.addView(bar)
        }
    }

    // ── Cleanup binding ──────────────────────────────────────────
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Data class ───────────────────────────────────────────────
    data class ReporteData(
        val mision: String,
        val tipoDocumento: String,
        val gravedad: String,
        val heridos: Int,
        val inconscientes: Int,
        val notas: String
    )
}