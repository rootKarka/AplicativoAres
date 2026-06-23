package com.tecsup.aresapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val btnLogin = findViewById<MaterialButton>(R.id.btn_login)
        val etEmail = findViewById<TextInputEditText>(R.id.et_email)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                // Aquí en el futuro validaremos con Django (Retrofit).
                // Por ahora, simulamos el ingreso exitoso.

                Toast.makeText(this, "Autenticación exitosa", Toast.LENGTH_SHORT).show()

                // 👇 ESTO ES EL INTENT EXPLÍCITO (Explicit Intent) 👇
                // Le decimos exactamente qué Activity abrir.
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)

                // Finalizamos el Login para que el usuario no pueda volver atrás con el botón físico
                finish()
            } else {
                Toast.makeText(this, "Por favor, ingresa credenciales", Toast.LENGTH_SHORT).show()
            }
        }
    }
}