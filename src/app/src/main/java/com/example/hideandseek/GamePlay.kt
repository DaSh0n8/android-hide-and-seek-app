package com.example.hideandseek

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.hideandseek.databinding.GamePlayBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions


class GamePlay : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var binding: GamePlayBinding

    // Google's API for location services
    private var fusedLocationClient: FusedLocationProviderClient? = null

    // configuration of all settings of FusedLocationProviderClient
    var locationRequest: LocationRequest? = null
    var locationCallBack: LocationCallback? = null
    private val Request_Code_Location = 22

    // interval in milliseconds for location updates
    private var updateInterval: Long = 10000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // location API settings
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.create()
        locationRequest!!.interval = updateInterval
        locationCallBack = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (locationResult != null) {
                    Log.d("LocationTest", "Location updates")
                    locationResult.lastLocation?.let { updateMap(it, map) }
                } else {
                    Log.d("LocationTest", "Location updates fail: null")
                }
            }
        }

        binding = GamePlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /**
     * Manipulates the map once available.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        updateLocation(map)
    }

    /**
     * Obtain the last location of the hider and update the map.
     */
    private fun updateLocation(googleMap: GoogleMap) {
        //if user grants permission
        if (ActivityCompat.checkSelfPermission(
                this@GamePlay,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient!!.requestLocationUpdates(
                locationRequest!!,
                locationCallBack!!,
                null
            )

            // get the last location
            fusedLocationClient!!.lastLocation
                .addOnSuccessListener(
                    this
                ) { location ->
                    if (location == null) {
                        Log.d("LocationTest", "null")
                    } else {
                        Log.d("LocationTest", "Success")
                        updateMap(location, googleMap) // if successful, update the UI
                    }
                }
        } else {
            //if user hasn't granted permission, ask for it explicitly
            ActivityCompat.requestPermissions(
                this@GamePlay,
                arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                Request_Code_Location
            )
        }
    }

    /**
     * Reflect the user's location on the map.
     */
    private fun updateMap(location: Location, googleMap: GoogleMap) {
        map = googleMap
        // extract the coordinates
        val lat = location.latitude
        val lon = location.longitude
        val user = LatLng(lat, lon)

        // convert the drawable user icon to a Bitmap
        val userIconBitmap = getBitmapFromVectorDrawable(this, R.drawable.user_icon)

        // add the marker and adjust the view
        map.addMarker(
            MarkerOptions()
                .position(user)
                .icon(BitmapDescriptorFactory.fromBitmap(userIconBitmap))
        )
        map.setMinZoomPreference(15F)
        map.moveCamera(CameraUpdateFactory.newLatLng(user))

        // set the map style
        map.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(this, R.raw.gamemap_lightmode)
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Request_Code_Location) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateLocation(map)
            }
        }
    }

    /**
     * Converts the drawables from vector form to Bitmap form.
     */
    private fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId)
        val bitmap = Bitmap.createBitmap(
            drawable!!.intrinsicWidth,
            drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}