package com.tecsup.aresapp.feature.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope // <-- Asegúrate de tener esta extensión
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.tecsup.aresapp.ui.MainActivity
import com.tecsup.aresapp.R
import com.tecsup.aresapp.data.RetrofitClient
import kotlinx.coroutines.launch // <-- Para disparar la corrutina

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // VERIFICAR SI YA EXISTE UNA SESIÓN ACTIVA
        val preferences = getSharedPreferences("ares_preferences", MODE_PRIVATE)
        val isLoggedIn = preferences.getBoolean("is_logged_in", false)

        if (isLoggedIn) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val btnLogin = findViewById<MaterialButton>(R.id.btn_login)
        val etEmail = findViewById<TextInputEditText>(R.id.et_email)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {

                // Ejecutamos la petición dentro de una corrutina asíncrona
                lifecycleScope.launch {
                    try {
                        val loginRequest = LoginRequest(email, password)

                        // LLAMADA SUSPENDIDA: Detiene la ejecución aquí sin congelar la UI
                        val response = RetrofitClient.instance.login(loginRequest)

                        if (response.isSuccessful && response.body()?.status == "success") {
                            val loginResponse = response.body()

                            // 💾 GUARDAR LA SESIÓN EN EL ALMACENAMIENTO LOCAL
                            val editor = preferences.edit()
                            editor.putBoolean("is_logged_in", true)
                            editor.putString("user_name", loginResponse?.usuario?.nombre)
                            editor.putString("user_role", loginResponse?.usuario?.rol)
                            editor.apply()

                            Toast.makeText(
                                this@LoginActivity,
                                "¡Bienvenido ${loginResponse?.usuario?.nombre}!",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Redirigir a la MainActivity
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            // Django devolvió un error (Credenciales incorrectas, cuenta inválida, etc.)
                            val codigoHttp = response.code()
                            val errorRaw = response.errorBody()?.string() ?: "Error desconocido"

                            Toast.makeText(
                                this@LoginActivity,
                                "Error $codigoHttp: $errorRaw",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                    } catch (e: Exception) {
                        // El servidor está caído, timeout, o el celular no tiene internet
                        Log.e("LoginActivity", "Error de red en login: ${e.message}", e)
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