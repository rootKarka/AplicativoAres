package com.tecsup.aresapp

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class BitacoraDialog : DialogFragment() {

    // ── 1. GESTIÓN DE PERMISOS DE MICRÓFONO ──
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { esConcedido ->
        if (esConcedido) {
            // Si el usuario acepta, lanzamos el micrófono
            lanzarReconocimientoDeVoz()
        } else {
            // Si rechaza, le avisamos sin que la app se cierre
            Toast.makeText(context, "⚠️ Se requiere el permiso de micrófono para grabar notas.", Toast.LENGTH_LONG).show()
        }
    }

    // ── 2. LAUNCHER DEL RECONOCIMIENTO DE VOZ NATIVO ──
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val resultados = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val textoReconocido = resultados?.firstOrNull()

            if (!textoReconocido.isNullOrBlank()) {
                guardarEntradaBitacora(textoReconocido, esVoz = true)
            }
            dismiss() // cierra el modal después de guardar
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_bitacora, null)

        val btnTexto = view.findViewById<MaterialCardView>(R.id.btn_bitacora_texto)
        val btnAudio = view.findViewById<MaterialCardView>(R.id.btn_bitacora_audio)
        val contenedorTexto = view.findViewById<android.widget.LinearLayout>(R.id.contenedor_texto)
        val etNotaTexto = view.findViewById<TextInputEditText>(R.id.et_nota_texto)
        val btnGuardarTexto = view.findViewById<MaterialButton>(R.id.btn_guardar_texto)

        // Opción "Escribir nota": muestra el campo de texto
        btnTexto.setOnClickListener {
            contenedorTexto.visibility = android.view.View.VISIBLE
        }

        // Opción "Grabar nota de voz": Primero verifica el permiso
        btnAudio.setOnClickListener {
            verificarPermisoDeAudio()
        }

        // Guardar la nota escrita manualmente
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

    // ── 3. LÓGICA DE VALIDACIÓN ──
    private fun verificarPermisoDeAudio() {
        // Consultamos a Android si ya tenemos el permiso concedido
        val estadoPermiso = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        )

        if (estadoPermiso == PackageManager.PERMISSION_GRANTED) {
            // Si ya lo tenemos, abrimos el dictado directamente
            lanzarReconocimientoDeVoz()
        } else {
            // Si no lo tenemos, lanzamos el cartel nativo de Android
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun lanzarReconocimientoDeVoz() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "PE")) // Configurado para Perú
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta la nota para la bitácora...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Reconocimiento de voz no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Aquí harías el POST real a Django:
     * POST /api/bitacora/
     * { mision, usuario, tipo_entrada: "NOTA", contenido: texto, es_voz: esVoz }
     */
    private fun guardarEntradaBitacora(texto: String, esVoz: Boolean) {
        val tipo = if (esVoz) "🎤 Voz" else "📝 Texto"
        Toast.makeText(context, "$tipo guardada: $texto", Toast.LENGTH_LONG).show()
        // TODO: implementar con Retrofit
    }

    companion object {
        const val TAG = "BitacoraDialog"
    }
}