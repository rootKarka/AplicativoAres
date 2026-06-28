package com.tecsup.aresapp.feature.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.tecsup.aresapp.R
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class DashboardFragment : Fragment() {

    private lateinit var txtTemp: TextView
    private lateinit var txtHumedad: TextView
    private lateinit var txtGas: TextView

    // Gráficos nativos
    private lateinit var chartTemp: LineChart
    private lateinit var chartHumedad: LineChart
    private lateinit var chartGas: LineChart

    private lateinit var client: WebSocketClient

    // Variables de tiempo simulado para el eje X de los gráficos
    private var timeIndexTemp = 0f
    private var timeIndexHumedad = 0f
    private var timeIndexGas = 0f

    // ── CONFIGURACIÓN DE UMBRALES DE SPRING BOOT ──
    private val limiteMaxTemp = 40.0 // Si pasa de 40°C dispara alerta
    private val limiteMaxGas = 300.0 // Si pasa de 300 PPM dispara alerta

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        txtTemp = view.findViewById(R.id.txtTemp)
        txtHumedad = view.findViewById(R.id.txtHumedad)
        txtGas = view.findViewById(R.id.txtGas)

        chartTemp = view.findViewById<LineChart>(R.id.chart_temp)
        chartHumedad = view.findViewById<LineChart>(R.id.chart_humedad)
        chartGas = view.findViewById<LineChart>(R.id.chart_gas)

        configurarGraficoEstilo(chartTemp, "Temperatura", ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
        configurarGraficoEstilo(chartHumedad, "Humedad", ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
        configurarGraficoEstilo(chartGas, "Gas GNV", ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))

        return view
    }

    override fun onStart() {
        super.onStart()
        conectarWebSocket()
    }

    private fun conectarWebSocket() {
        if (::client.isInitialized && client.isOpen) {
            return
        }

        client = object : WebSocketClient(URI("ws://10.147.188.78:8000/ws/sensores/")) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d("WS", "WebSocket conectado exitosamente")
            }

            override fun onMessage(message: String?) {
                if (message != null && isAdded) {
                    try {
                        val json = JSONObject(message)
                        val tipo = json.getString("tipo")
                        val valor = json.getDouble("valor")

                        activity?.runOnUiThread {
                            when (tipo) {
                                "TEMPERATURA" -> {
                                    txtTemp.text = "$valor °C"
                                    agregarPuntoAGrafico(chartTemp, timeIndexTemp++, valor.toFloat())
                                    evaluarAlertasCriticas("TEMPERATURA", valor)
                                }
                                "HUMEDAD" -> {
                                    txtHumedad.text = "$valor %"
                                    agregarPuntoAGrafico(chartHumedad, timeIndexHumedad++, valor.toFloat())
                                }
                                "GAS" -> {
                                    txtGas.text = "$valor PPM"
                                    agregarPuntoAGrafico(chartGas, timeIndexGas++, valor.toFloat())
                                    evaluarAlertasCriticas("GAS", valor)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("WS", "Error procesando JSON", e)
                    }
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d("WS", "WebSocket cerrado")
            }

            override fun onError(ex: Exception?) {
                Log.e("WS", "Error en WebSocket", ex)
            }
        }
        client.connect()
    }

    // Estiliza los gráficos para que combinen con la estética oscura de ARES
    private fun configurarGraficoEstilo(chart: LineChart, label: String, color: Int) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(false)
        chart.setDrawGridBackground(false)

        // Colores de los ejes adaptados a modo oscuro
        chart.xAxis.textColor = ContextCompat.getColor(requireContext(), android.R.color.white)
        chart.axisLeft.textColor = ContextCompat.getColor(requireContext(), android.R.color.white)
        chart.axisRight.isEnabled = false
        chart.legend.textColor = ContextCompat.getColor(requireContext(), android.R.color.white)

        val entries = ArrayList<Entry>()
        val dataSet = LineDataSet(entries, label).apply {
            this.color = color
            setCircleColor(color)
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
        }
        chart.data = LineData(dataSet)
        chart.invalidate()
    }

    // Inserta datos en vivo al gráfico y remueve los viejos para retener solo los últimos minutos
    private fun agregarPuntoAGrafico(chart: LineChart, x: Float, y: Float) {
        val data = chart.data ?: return

        data.addEntry(Entry(x, y), 0)
        data.notifyDataChanged()
        chart.notifyDataSetChanged()

        // Muestra un rango máximo de 60 muestras (equivalente a 1 - 2 minutos según la tasa de refresco)
        chart.setVisibleXRangeMaximum(60f)
        chart.moveViewToX(x)
    }

    // Lógica para interceptar los umbrales de Spring Boot
    private fun evaluarAlertasCriticas(tipo: String, valor: Double) {
        if (tipo == "TEMPERATURA" && valor >= limiteMaxTemp) {
            dispararNotificacionDeAlerta("⚠️ TEMP. CRÍTICA: $valor °C detectados en la zona.")
        } else if (tipo == "GAS" && valor >= limiteMaxGas) {
            dispararNotificacionDeAlerta("🚨 FUGA DE GAS: Presencia peligrosa de $valor PPM.")
        }
    }

    private fun dispararNotificacionDeAlerta(mensaje: String) {
        // TODO: Aquí llamarás al sistema de campana superior e inflarás el banner flotante "Heads-up"
        // Por ahora lo dejamos registrado en consola para asegurar su funcionamiento.
        Log.w("ALERTAS_ARES", mensaje)
    }

    override fun onStop() {
        super.onStop()
        if (::client.isInitialized && client.isOpen) {
            client.close()
        }
    }
}