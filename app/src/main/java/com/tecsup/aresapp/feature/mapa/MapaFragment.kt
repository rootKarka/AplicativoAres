package com.tecsup.aresapp.feature.mapa

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.card.MaterialCardView
import com.tecsup.aresapp.R

class MapaFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mapa, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configurar el botón de retroceso
        val btnBack = view.findViewById<MaterialCardView>(R.id.btn_back_map)
        btnBack.setOnClickListener {
            // Esto le dice a Android: "Regresa al Fragmento anterior (Control)"
            parentFragmentManager.popBackStack()
        }

        // Inicializar el mapa
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // MAPA NORMAL (Diseño plano de calles)
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL

        // Coordenadas simuladas para probar
        val latitudRobotFisico = -16.4258
        val longitudRobotFisico = -71.5035

        val posicionRobot = LatLng(latitudRobotFisico, longitudRobotFisico)

        mMap.addMarker(
            MarkerOptions()
            .position(posicionRobot)
            .title("ARES-01")
            .snippet("Última ubicación conocida"))

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(posicionRobot, 18f))
    }
}