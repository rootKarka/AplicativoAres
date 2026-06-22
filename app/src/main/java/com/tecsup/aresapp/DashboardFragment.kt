package com.tecsup.aresapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class DashBoard : Fragment() {

    private lateinit var txtTemp: TextView
    private lateinit var txtGas: TextView
    private lateinit var client: WebSocketClient

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(
            R.layout.fragment_dash_board,
            container,
            false
        )

        txtTemp = view.findViewById(R.id.txtTemp)
        txtGas = view.findViewById(R.id.txtGas)

        return view
    }

    override fun onStart() {
        super.onStart()
        conectarWebSocket()
    }

    private fun conectarWebSocket() {
        // Evita crear múltiples conexiones
        if (::client.isInitialized && client.isOpen) {
            return
        }

        client = object : WebSocketClient(
            URI("ws://192.168.100.32:8000/ws/sensores/")
        ) {

            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d("WS", "WebSocket conectado")
            }

            override fun onMessage(message: String?) {
                if (message != null && isAdded) {
                    val json = JSONObject(message)

                    val tipo = json.getString("tipo")
                    val valor = json.getDouble("valor")

                    activity?.runOnUiThread {
                        when (tipo) {
                            "TEMPERATURA" -> {
                                txtTemp.text = "$valor °C"
                            }

                            "GAS" -> {
                                txtGas.text = "$valor PPM"
                            }
                        }
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

    override fun onStop() {
        super.onStop()

        if (::client.isInitialized && client.isOpen) {
            client.close()
        }
    }
}