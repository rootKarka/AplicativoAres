package com.tecsup.aresapp.feature.diagnostico

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.tecsup.aresapp.R

class Diagnostico : Fragment() {

    private var cardPorHacer: MaterialCardView? = null
    private var btnVista: MaterialButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Infla el layout del fragmento
        return inflater.inflate(R.layout.fragment_diagnostico, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar las vistas
        cardPorHacer = view.findViewById(R.id.cardPorHacer)
        btnVista = view.findViewById(R.id.btnVista)

        // Configurar el botón
        btnVista?.setOnClickListener {
            cardPorHacer?.animate()
                ?.alpha(0f)
                ?.translationY(120f)
                ?.setDuration(350)
                ?.withEndAction {
                    cardPorHacer?.visibility = View.GONE
                }
                ?.start()
        }
    }
}