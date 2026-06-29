package com.tecsup.aresapp.feature.cuenta

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import android.content.Intent
import com.google.android.material.button.MaterialButton
import com.tecsup.aresapp.R
import com.tecsup.aresapp.feature.login.LoginActivity

class CuentaLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        // 1. Inflamos el XML de la cuenta dentro de este componente
        LayoutInflater.from(context).inflate(R.layout.activity_cuenta, this, true)

        orientation = VERTICAL

        // 2. Buscamos el botón de cerrar sesión
        val btnCerrarSesion = findViewById<MaterialButton>(R.id.btnCerrarSesion)

        // 3. Asignamos la acción al hacer clic
        btnCerrarSesion?.setOnClickListener {
            val preferences = context.getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)
            val editor = preferences.edit()

            // Limpiamos los datos de sesión
            editor.clear()
            editor.apply()

            ejecutarCierreDeSesionLocal()
        }
    }

    private fun ejecutarCierreDeSesionLocal() {
        Toast.makeText(context, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()

        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)

        // 👇 SOLUCIÓN AQUÍ: Verificamos si el contexto es una Activity y hacemos el cast seguro
        (context as? Activity)?.finish()
    }
}