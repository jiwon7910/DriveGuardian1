package com.example.poseexercise.backgroundlocationtracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.poseexercise.R
import com.google.android.gms.maps.CameraUpdateFactory // 추가된 부분
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class HomeFragment : Fragment(R.layout.fragment_home), LocationListener {
    private var googleMap: GoogleMap? = null
    private var myPosition: LatLng? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeMap()
        clickStart()
        clickStop()
    }

    private fun initializeMap() {
        val fm = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        fm.getMapAsync { map ->
            googleMap = map
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 권한 요청
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
                return@getMapAsync
            }
            googleMap?.isMyLocationEnabled = true

            // 이화여자대학교의 좌표를 설정
            val ewhaPosition = LatLng(37.5610, 126.9468)
            myPosition = ewhaPosition

            // 이화여자대학교에 마커 추가 및 카메라 이동
            googleMap?.addMarker(MarkerOptions().position(myPosition!!).title("Ewha Womans University"))
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(myPosition, 18f))

            // 로그 추가
            Log.d("HomeFragment", "Map initialized with Ewha Womans University")
        }
    }

    override fun onLocationChanged(location: Location) {
        location.let {
            val latitude: Double = it.latitude
            val longitude: Double = it.longitude
            myPosition = LatLng(latitude, longitude)
            googleMap?.clear()
            googleMap?.addMarker(MarkerOptions().position(myPosition!!).title("Current Location"))
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(myPosition, 18f))

            // 로그 추가
            Log.d("HomeFragment", "Location changed: $latitude, $longitude")
        }
    }


    private fun clickStart() {
        val clickStart = view?.findViewById<Button>(R.id.start_button)
        clickStart?.setOnClickListener {
            val intent = Intent(requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_START
                requireContext().startService(this)
            }
        }
    }

    private fun clickStop() {
        val clickStop = view?.findViewById<Button>(R.id.stop_button)
        clickStop?.setOnClickListener {
            val intent = Intent(requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_STOP
                requireContext().startService(this)
            }
        }
    }



    override fun onProviderEnabled(provider: String) {
    }

    override fun onProviderDisabled(provider: String) {
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1 // 추가된 부분
    }
}
