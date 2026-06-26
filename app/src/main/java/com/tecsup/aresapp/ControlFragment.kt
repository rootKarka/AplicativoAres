package com.tecsup.aresapp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tecsup.aresapp.databinding.FragmentControlBinding
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class Control : Fragment() {

    private var _binding: FragmentControlBinding? = null
    private val binding get() = _binding!!

    private val esp32Ip = "192.168.1.38"
    private val port = 1234
    private var lastSentTime = 0L
    private val sendInterval = 50L

    private var isCameraMode = false

    // ID de la misión y robot actuales — esto deberías recibirlo como argumento
    // del Fragment (Bundle) según en qué misión esté trabajando el operador.
    // Por ahora lo dejamos como placeholder.
    private val misionId: Int? = null
    private val robotId: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWebView()
        setupButtons()
        setupJoystick()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webviewStream
        webView.webViewClient = WebViewClient()
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true

        val streamUrl = "http://$esp32Ip:81/stream"
        webView.loadUrl(streamUrl)
    }

    private fun setupButtons() {
        binding.btnSwitch.setOnClickListener {
            isCameraMode = !isCameraMode
            val modeText = if (isCameraMode) "CÁMARA" else "MOTOR"
            binding.tvModeIndicator.text = modeText

            if (isCameraMode) {
                binding.btnSwitch.strokeColor = resources.getColor(R.color.ares_green, null)
                binding.btnSwitch.strokeWidth = 2
            } else {
                binding.btnSwitch.strokeWidth = 0
            }
            Toast.makeText(context, "Joystick controla: $modeText", Toast.LENGTH_SHORT).show()
        }

        // Botón Cámara: captura el frame actual del stream del robot
        binding.btnCamara.setOnClickListener {
            capturarFrameDelRobot()
        }

        binding.btnVideo.setOnClickListener {
            Toast.makeText(context, "🎥 Grabando video de misión...", Toast.LENGTH_SHORT).show()
        }

        // Micrófono: hablar a través del robot con la víctima (NO transcripción)
        binding.btnMic.setOnClickListener {
            Toast.makeText(context, "🎤 Canal de voz abierto con el robot...", Toast.LENGTH_SHORT).show()
            // TODO: aquí va la lógica de streaming de audio UDP hacia el ESP32,
            // distinta del reconocimiento de voz de la bitácora.
        }

        binding.btnMapa.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, MapaFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    /**
     * Captura visualmente lo que el WebView está mostrando en este momento
     * (el frame actual del stream MJPEG del robot) y lo guarda como evidencia.
     */
    private fun capturarFrameDelRobot() {
        val webView = binding.webviewStream

        try {
            val bitmap = Bitmap.createBitmap(
                webView.width,
                webView.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            webView.draw(canvas)

            val archivo = guardarBitmapComoArchivo(bitmap)
            if (archivo != null) {
                Toast.makeText(context, "📸 Captura guardada en Evidencias", Toast.LENGTH_SHORT).show()
                subirEvidenciaADjango(archivo, origenRobot = true)
            } else {
                Toast.makeText(context, "Error al guardar la captura", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("Control", "Error capturando frame: ${e.message}")
            Toast.makeText(context, "No se pudo capturar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarBitmapComoArchivo(bitmap: Bitmap): File? {
        return try {
            val carpeta = requireContext().getExternalFilesDir("Pictures/ARES")
            if (carpeta != null && !carpeta.exists()) carpeta.mkdirs()

            val nombreArchivo = "ARES_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())}.jpg"
            val archivo = File(carpeta, nombreArchivo)

            FileOutputStream(archivo).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            archivo
        } catch (e: Exception) {
            Log.e("Control", "Error guardando archivo: ${e.message}")
            null
        }
    }

    /**
     * Sube el archivo de evidencia a Django.
     * Tu modelo Evidencia: mision, robot, usuario, tipo, archivo, descripcion, latitud, longitud
     * Si origenRobot=true → robot lleno, usuario vacío (foto capturada del stream del robot)
     * Si origenRobot=false → usuario lleno, robot vacío (foto tomada por el operador del entorno)
     */
    private fun subirEvidenciaADjango(archivo: File, origenRobot: Boolean) {
        // TODO: implementar con Retrofit cuando esté configurado.
        // Ejemplo de multipart/form-data que necesitarás:
        //
        // val requestBody = archivo.asRequestBody("image/jpeg".toMediaTypeOrNull())
        // val filePart = MultipartBody.Part.createFormData("archivo", archivo.name, requestBody)
        //
        // val misionPart = misionId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        // val tipoPart   = "FOTO".toRequestBody("text/plain".toMediaTypeOrNull())
        // val robotPart  = if (origenRobot) robotId.toString().toRequestBody(...) else null
        //
        // apiService.crearEvidencia(misionPart, robotPart, tipoPart, filePart, ...)

        Log.d("Control", "Evidencia lista para subir: ${archivo.absolutePath} (origenRobot=$origenRobot)")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupJoystick() {
        binding.joystickContainer.setOnTouchListener { _, event ->
            val base = binding.joystickBase
            val handle = binding.joystickHandle

            val centerX = base.x + base.width / 2f
            val centerY = base.y + base.height / 2f
            val radius = base.width / 2f

            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    var dx = event.x - centerX
                    var dy = event.y - centerY

                    val distance = hypot(dx, dy)
                    if (distance > radius) {
                        val angle = atan2(dy, dx)
                        dx = (cos(angle) * radius)
                        dy = (sin(angle) * radius)
                    }

                    handle.translationX = dx
                    handle.translationY = dy

                    val normalizedX = dx / radius
                    val normalizedY = -dy / radius

                    updateControlValues(normalizedX, normalizedY)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handle.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(150)
                        .start()
                    updateControlValues(0f, 0f)
                }
            }
            true
        }
    }

    private fun updateControlValues(x: Float, y: Float) {
        val throttle = -y
        val steering = -x

        var leftMotor = throttle + steering
        var rightMotor = throttle - steering

        val max = Math.max(Math.abs(leftMotor), Math.abs(rightMotor))
        if (max > 1.0f) {
            leftMotor /= max
            rightMotor /= max
        }

        val valX = (leftMotor * 255).toInt()
        val valY = (rightMotor * 255).toInt()

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSentTime > sendInterval || (valX == 0 && valY == 0)) {
            val prefix = if (isCameraMode) "CAM" else "MOT"
            sendToESP32(prefix, valX, valY)
            lastSentTime = currentTime
        }
    }

    private fun sendToESP32(prefix: String, x: Int, y: Int) {
        Thread {
            try {
                val message = "$prefix,$x,$y\n"
                val address = InetAddress.getByName(esp32Ip)
                val socket = DatagramSocket()
                val data = message.toByteArray()
                val packet = DatagramPacket(data, data.size, address, port)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                Log.e("UDP", "Error enviando datos: ${e.message}")
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = Control()
    }
}