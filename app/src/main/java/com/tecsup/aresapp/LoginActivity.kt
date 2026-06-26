package com.tecsup.aresapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // VERIFICAR SI YA EXISTE UNA SESIÓN ACTIVA
        val preferences = getSharedPreferences("ares_preferences", MODE_PRIVATE)
        val isLoggedIn = preferences.getBoolean("is_logged_in", false)

        if (isLoggedIn) {
            // Si ya está logueado, lo mandamos directo a la MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Cerramos LoginActivity para que no pueda volver atrás
            return // Detiene la ejecución del resto de onCreate
        }

        setContentView(R.layout.activity_login)

        val btnLogin = findViewById<MaterialButton>(R.id.btn_login)
        val etEmail = findViewById<TextInputEditText>(R.id.et_email)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {

                // 1. Preparamos el objeto con las credenciales
                val loginRequest = LoginRequest(email, password)

                // 2. Realizamos la petición asíncrona mediante Retrofit
                RetrofitClient.instance.login(loginRequest).enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                        if (response.isSuccessful && response.body()?.status == "success") {
                            val loginResponse = response.body()

                            // 💾 GUARDAR LA SESIÓN EN EL ALMACENAMIENTO LOCAL
                            val preferences = getSharedPreferences("ares_preferences", MODE_PRIVATE)
                            val editor = preferences.edit()

                            editor.putBoolean("is_logged_in", true) // Marcar que hay una sesión activa
                            editor.putString("user_name", loginResponse?.usuario?.nombre)
                            editor.putString("user_role", loginResponse?.usuario?.rol)

                            editor.apply() // Guarda los datos en segundo plano de manera segura

                            Toast.makeText(this@LoginActivity, "¡Bienvenido ${loginResponse?.usuario?.nombre}!", Toast.LENGTH_SHORT).show()

                            // Redirigir a la MainActivity
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            // Cuando hay error, la respuesta de Django viene en .errorBody(), no en .body()
                            val codigoHttp = response.code() // Te dirá si es 400, 404, 500, etc.
                            val errorRaw = response.errorBody()?.string()

                            Toast.makeText(this@LoginActivity, "Error $codigoHttp: $errorRaw", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        // Manejo de fallos de red (ej: el servidor Django está caído)
                        Toast.makeText(this@LoginActivity, "Error de conexión: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                })

            } else {
                Toast.makeText(this, "Por favor, ingresa credenciales", Toast.LENGTH_SHORT).show()
            }
        }
    }
}