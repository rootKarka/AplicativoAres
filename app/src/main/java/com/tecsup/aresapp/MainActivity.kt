package com.tecsup.aresapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.tecsup.aresapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Obtener el NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        // 2. Obtener el NavController
        val navController = navHostFragment.navController

        // 3. Vincular automáticamente el BottomNavigationView con el NavController
        // Esto hace que al tocar un item, se navegue al fragmento con el mismo ID
        binding.menuNav.setupWithNavController(navController)

        // Escuchar los clics en la barra superior
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_cuenta -> {
                    // Aquí le decimos al NavController que nos lleve a la pantalla de cuenta
                    // (Asegúrate de haber creado el Fragmento de Cuenta y haberlo agregado a tu nav_graph.xml con este ID)
                    navController.navigate(R.id.nav_cuenta)
                    true
                }
                else -> false
            }
        }
    }


}