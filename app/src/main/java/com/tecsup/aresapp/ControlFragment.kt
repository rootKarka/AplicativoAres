package com.tecsup.aresapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tecsup.aresapp.databinding.FragmentControlBinding
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class Control : Fragment() {

    private var _binding: FragmentControlBinding? = null
    private val binding get() = _binding!!

    // Configuración UDP
    private val esp32Ip = "192.168.4.1" // IP por defecto del ESP32 en modo AP
    private val port = 1234
    private var lastSentTime = 0L
    private val sendInterval = 50L // Enviar cada 50ms para no saturar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupJoystick()
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

        val leftPWM = (leftMotor * 255).toInt()
        val rightPWM = (rightMotor * 255).toInt()

        Log.d("Joystick", "L: $leftPWM, R: $rightPWM")
        
        // Enviar solo si ha pasado el intervalo o si es una parada (0,0)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSentTime > sendInterval || (leftPWM == 0 && rightPWM == 0)) {
            sendToESP32(leftPWM, rightPWM)
            lastSentTime = currentTime
        }
    }

    private fun sendToESP32(left: Int, right: Int) {
        Thread {
            try {
                val message = "$left,$right\n"
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
