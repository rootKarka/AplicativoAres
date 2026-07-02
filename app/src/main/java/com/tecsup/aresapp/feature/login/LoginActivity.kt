package com.tecsup.aresapp.feature.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.tecsup.aresapp.ui.MainActivity
import com.tecsup.aresapp.R
import com.tecsup.aresapp.data.RetrofitClient
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // VERIFICAR SI YA EXISTE UNA SESIÓN ACTIVA
        val preferences = getSharedPreferences("ares_preferences", MODE_PRIVATE)
        val isLoggedIn  = preferences.getBoolean("is_logged_in", false)

        if (isLoggedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val btnLogin  = findViewById<MaterialButton>(R.id.btn_login)
        val etEmail   = findViewById<TextInputEditText>(R.id.et_email)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)

        btnLogin.setOnClickListener {
            val email    = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.instance.login(LoginRequest(email, password))

                        if (response.isSuccessful && response.body()?.status == "success") {
                            val loginResponse = response.body()
                            val usuario       = loginResponse?.usuario

                            // Guardar sesión en SharedPreferences
                            preferences.edit().apply {
                                putBoolean("is_logged_in", true)
                                putString("user_name",     usuario?.nombre)
                                putString("user_email",    email)
                                putString("user_role",     usuario?.rol)
                                putInt("autor_id",         usuario?.id ?: 1)  // ← ID del operador
                                putInt("mision_id",        1)                 // TODO: misión activa
                                apply()
                            }

                            Toast.makeText(
                                this@LoginActivity,
                                "¡Bienvenido ${usuario?.nombre}!",
                                Toast.LENGTH_SHORT
                            ).show()

                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()

                        } else {
                            val codigoHttp = response.code()
                            val errorRaw   = response.errorBody()?.string() ?: "Error desconocido"
                            Toast.makeText(
                                this@LoginActivity,
                                "Error $codigoHttp: $errorRaw",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Error de red: ${e.message}", e)
                        Toast.makeText(
                            this@LoginActivity,
                            "No se pudo conectar al servidor. Verifica tu conexión.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                Toast.makeText(this, "Por favor, ingresa credenciales", Toast.LENGTH_SHORT).show()
            }
        }
    }
}