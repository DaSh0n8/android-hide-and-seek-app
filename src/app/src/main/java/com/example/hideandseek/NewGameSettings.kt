package com.example.hideandseek

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.hideandseek.databinding.NewGameSettingsBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.slider.Slider
import com.google.firebase.database.FirebaseDatabase
import java.util.Random

class NewGameSettings : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private lateinit var binding: NewGameSettingsBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var locationHelper: LocationHelper

    // default geofence radius
    private var geofenceRadius = 100.0
    private var userLatLng: LatLng = LatLng(0.0, 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = NewGameSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request location updates
        locationHelper = LocationHelper(this, (1*60*1000))
        locationHelper.requestLocationUpdates { location ->
            userLatLng = LatLng(location.latitude, location.longitude)
            updateMap(userLatLng, map)
        }

        // obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // get firebase real time db
        val application = application as HideAndSeek
        database = application.getRealtimeDb()

        // observe the changes on the slider
        val discreteSlider: Slider = findViewById(R.id.discreteSlider)
        discreteSlider.addOnChangeListener { _, radius, _ ->
            // update the visible geofence on map as it goes
            geofenceRadius = radius.toDouble()
            updateMap(userLatLng, map)
        }

        val createGameButton: Button = findViewById(R.id.btnStartGame)
        val receivedUsername: String? = intent.getStringExtra("username_key")
        val receivedUserIcon: ByteArray? = intent.getByteArrayExtra("userIcon")
        createGameButton.setOnClickListener {
            NetworkUtils.checkConnectivityAndProceed(this) {
                createButtonClicked(receivedUsername, receivedUserIcon)
            }
        }
    }

    /**
     * Create game session with user input values
     */
    private fun createButtonClicked(username: String?, userIcon: ByteArray?){
        if (username == null){
            return
        }
        val hidingTimeInput: EditText = findViewById(R.id.editHidingTime)
        val hidingTime: String = hidingTimeInput.text.toString().trim()

        val updateIntervalInput: EditText = findViewById(R.id.editUpdateInterval)
        val updateInterval: String = updateIntervalInput.text.toString().trim()

        val gameTimeInput: EditText = findViewById(R.id.editGameTime)
        val gameTime: String = gameTimeInput.text.toString().trim()

        val radiusInput: Slider = findViewById(R.id.discreteSlider)
        val geofenceRadius: Float = radiusInput.value

        val gameSessionRef = database.getReference("gameSessions").push()

        val randomNum = ((0..9999).random())
        val playerCode = String.format("%04d",randomNum)

        val players = listOf(
            PlayerClass(username, true, null, null, false, true, playerCode, null)
        )

        val sessionId: Int = Random().nextInt(999999 - 100000 + 1) + 100000
        val sessionIdString: String = sessionId.toString()
        if (updateInterval.isBlank() || hidingTime.isBlank() || gameTime.isBlank()) {
            Toast.makeText(this@NewGameSettings, "All fields are required", Toast.LENGTH_SHORT).show()
        } else if (updateInterval.toInt() < 1 || hidingTime.toInt() < 1 || gameTime.toInt() < 1){
            Toast.makeText(this@NewGameSettings, "Input values have to be more than 0", Toast.LENGTH_SHORT).show()
        } else {
            val newGameSession = GameSessionClass(sessionIdString, "ongoing", players, gameTime.toInt(), hidingTime.toInt() ,updateInterval.toInt(), geofenceRadius.toInt(), userLatLng.latitude, userLatLng.longitude)
            gameSessionRef.setValue(newGameSession)

            val intent = Intent(this, Lobby::class.java)
            intent.putExtra("userIcon", userIcon)
            intent.putExtra("lobby_key", sessionIdString)
            intent.putExtra("username_key", username)
            intent.putExtra("host", true)

            startActivity(intent)
            locationHelper.stopUpdate()
            finish()
        }

    }

    /**
     * Manipulates the map once available with latest values.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
    }

    /**
     * Reflect the user's location on the map with virtual geofence drawn.
     */
    private fun updateMap(user: LatLng, googleMap: GoogleMap) {
        // clear previous drawings the map
        map = googleMap
        map.clear()

        // convert the drawable user icon to a Bitmap
        val userIconBitmap = getBitmapFromVectorDrawable(this, R.drawable.self_user_icon)

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

        // add the geofence
        map.addCircle(
            CircleOptions()
                .center(user)
                .radius(geofenceRadius) // Radius in meters
                .strokeColor(Color.RED) // Circle border color
                .fillColor(Color.argb(60, 220, 0, 0)) // Fill color with transparency
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LocationHelper.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start location updates
                locationHelper.requestLocationUpdates { location ->
                    userLatLng = LatLng(location.latitude, location.longitude)
                    updateMap(userLatLng, map)
                }
            } else {
                // Permission denied, handle accordingly
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
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