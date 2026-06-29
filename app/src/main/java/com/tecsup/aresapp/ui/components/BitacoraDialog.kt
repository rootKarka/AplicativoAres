package com.tecsup.aresapp.ui.components

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.tecsup.aresapp.R
import com.tecsup.aresapp.data.BitacoraDto
import com.tecsup.aresapp.data.BitacoraRequest
import com.tecsup.aresapp.data.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class BitacoraDialog : DialogFragment() {

    // ── Datos que recibe desde el Fragment que lo abre ────────────
    private var misionId: Int = 1
    private var autorId:  Int = 1

    // ── Callback para notificar al Fragment cuando se guarda ──────
    // Así ReporteFragment puede actualizar la bitácora en pantalla
    var onEntradaGuardada: ((texto: String, tipo: String, esVoz: Boolean) -> Unit)? = null

    // ── 1. GESTIÓN DE PERMISOS DE MICRÓFONO ──────────────────────
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { esConcedido ->
        if (esConcedido) {
            lanzarReconocimientoDeVoz()
        } else {
            Toast.makeText(context, "⚠️ Se requiere el permiso de micrófono para grabar notas.", Toast.LENGTH_LONG).show()
        }
    }

    // ── 2. LAUNCHER DEL RECONOCIMIENTO DE VOZ ────────────────────
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultados = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val textoReconocido = resultados?.firstOrNull()
            if (!textoReconocido.isNullOrBlank()) {
                guardarEntradaBitacora(textoReconocido, esVoz = true)
            }
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view     = inflater.inflate(R.layout.dialog_bitacora, null)

        val btnTexto        = view.findViewById<MaterialCardView>(R.id.btn_bitacora_texto)
        val btnAudio        = view.findViewById<MaterialCardView>(R.id.btn_bitacora_audio)
        val contenedorTexto = view.findViewById<LinearLayout>(R.id.contenedor_texto)
        val etNotaTexto     = view.findViewById<TextInputEditText>(R.id.et_nota_texto)
        val btnGuardarTexto = view.findViewById<MaterialButton>(R.id.btn_guardar_texto)

        btnTexto.setOnClickListener {
            contenedorTexto.visibility = View.VISIBLE
        }

        btnAudio.setOnClickListener {
            verificarPermisoDeAudio()
        }

        btnGuardarTexto.setOnClickListener {
            val texto = etNotaTexto.text.toString().trim()
            if (texto.isNotEmpty()) {
                guardarEntradaBitacora(texto, esVoz = false)
                dismiss()
            } else {
                Toast.makeText(context, "Escribe algo antes de guardar", Toast.LENGTH_SHORT).show()
            }
        }

        val dialog = Dialog(requireContext())
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }

    // ── 3. LÓGICA DE VALIDACIÓN ───────────────────────────────────
    private fun verificarPermisoDeAudio() {
        val estadoPermiso = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        )
        if (estadoPermiso == PackageManager.PERMISSION_GRANTED) {
            lanzarReconocimientoDeVoz()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun lanzarReconocimientoDeVoz() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "PE"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta la nota para la bitácora...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Reconocimiento de voz no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    // ── 4. GUARDAR EN DJANGO via Retrofit ─────────────────────────
    private fun guardarEntradaBitacora(texto: String, esVoz: Boolean) {
        val tipo = when {
            esVoz -> "NOTA"   // voz transcrita → tipo NOTA
            else  -> "NOTA"   // texto manual   → tipo NOTA
            // El operador puede cambiar el tipo desde la UI si se quiere
        }

        val request = BitacoraRequest(
            mision       = misionId,
            usuario      = autorId,
            tipo_entrada = tipo,
            contenido    = texto,
            es_voz       = esVoz,
            // GPS del operador — opcional, se puede obtener con LocationManager
            latitud      = 0.0,
            longitud     = 0.0,
        )

        RetrofitClient.instance.postBitacora(request)
            .enqueue(object : Callback<BitacoraDto> {
                override fun onResponse(
                    call: Call<BitacoraDto>,
                    response: Response<BitacoraDto>
                ) {
                    if (response.isSuccessful) {
                        // Notificar al Fragment para que actualice la lista
                        onEntradaGuardada?.invoke(texto, tipo, esVoz)
                        val tipoLabel = if (esVoz) "🎤 Voz" else "📝 Texto"
                        Toast.makeText(context, "$tipoLabel guardada en bitácora", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Error al guardar: ${response.code()}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFailure(call: Call<BitacoraDto>, t: Throwable) {
                    Toast.makeText(context, "Sin conexión: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    companion object {
        const val TAG = "BitacoraDialog"

        // Factory para crear el dialog con los datos necesarios
        fun newInstance(misionId: Int, autorId: Int): BitacoraDialog {
            return BitacoraDialog().apply {
                this.misionId = misionId
                this.autorId  = autorId
            }
        }
    }
}