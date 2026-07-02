package com.tecsup.aresapp.feature.cuenta

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.tecsup.aresapp.ui.MainActivity
import com.tecsup.aresapp.R

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class CuentaFragment : Fragment() {
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.activity_cuenta, container, false)
    }

    // 👇 AQUÍ AGREGAMOS LA LÓGICA DEL BOTÓN CUANDO LA VISTA YA ESTÁ CREADA 👇
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Cargar datos del usuario desde SharedPreferences
        cargarDatosUsuario(view)

        // 1. Encontramos el MaterialButton usando el ID de tu fragment_cuenta.xml
        val btnCerrarSesion = view.findViewById<MaterialButton>(R.id.btnCerrarSesion)

        // 2. Le asignamos la acción al hacer clic
        btnCerrarSesion?.setOnClickListener {
            // Accedemos a las SharedPreferences con el contexto de la actividad
            val preferences = requireActivity().getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)
            val editor = preferences.edit()

            // Limpiamos los datos del usuario logueado por completo
            editor.clear()
            editor.apply()

            // Llamamos al método público que agregamos en tu MainActivity
            (requireActivity() as MainActivity).ejecutarCierreDeSesion()
        }
    }

    override fun onResume() {
        super.onResume()
        // Sincronizar switch cuando el fragment se vuelve visible
        view?.let { cargarDatosUsuario(it) }
    }

    private fun cargarDatosUsuario(view: View) {
        val preferences = requireActivity().getSharedPreferences("ares_preferences", Context.MODE_PRIVATE)

        // Obtener datos del usuario
        val nombre = preferences.getString("user_name", "Operador")
        val email = preferences.getString("user_email", "")
        val rol = preferences.getString("user_role", "OPERADOR")
        val autorId = preferences.getInt("autor_id", 0)

        // Actualizar UI
        view.findViewById<android.widget.TextView>(R.id.tvNombreOperador)?.text = nombre
        view.findViewById<android.widget.TextView>(R.id.tvCorreoOperador)?.text = email
        view.findViewById<android.widget.TextView>(R.id.tvRolOperador)?.text = rol
        view.findViewById<android.widget.TextView>(R.id.tvIdOperador)?.text = "ID: $autorId"

        // Generar iniciales para el avatar
        val iniciales = nombre?.take(2)?.uppercase() ?: "OP"
        view.findViewById<android.widget.TextView>(R.id.tvAvatarIniciales)?.text = iniciales

        // Configurar switch de modo oscuro
        val switchModoOscuro = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchModoOscuro)
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        switchModoOscuro?.isChecked = isNightMode

        // Habilitar click en el switch
        switchModoOscuro?.isClickable = true
        switchModoOscuro?.isFocusable = true

        switchModoOscuro?.setOnCheckedChangeListener { _, isChecked ->
            // Guardar preferencia
            preferences.edit().putBoolean("dark_mode", isChecked).apply()

            // Cambiar tema
            if (isChecked) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            }

            // Actualizar icono en MainActivity
            (requireActivity() as MainActivity).updateThemeIcon()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            CuentaFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}