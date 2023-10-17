package com.example.hideandseek

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.hideandseek.NetworkUtils.Companion.checkConnectivityAndProceed
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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LobbySettings : AppCompatActivity(), OnMapReadyCallback {
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

        val receivedLobbyCode: String? = intent.getStringExtra("lobby_code_key")
        val receivedUsername: String? = intent.getStringExtra("username_key")
        val host = intent.getBooleanExtra("host", false)
        val lobbyHeader = findViewById<TextView>(R.id.titleText)
        lobbyHeader.text = "Change Game Setting"

        // Request location updates
        locationHelper = LocationHelper(this, (1*60*1000))
        locationHelper.requestLocationUpdates { location ->
            Log.d("See location", location.toString())
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
        createGameButton.setOnClickListener {
            checkConnectivityAndProceed(this) {
                confirmSettingsClicked(receivedLobbyCode, receivedUsername, host)
            }
        }

        loadSettingsInFields(receivedLobbyCode)
    }

    private fun loadSettingsInFields(lobbyCode: String?){
        if (lobbyCode == null){
            return
        }
        val hidingTimeInput: EditText = findViewById(R.id.editHidingTime)
        val updateIntervalInput: EditText = findViewById(R.id.editUpdateInterval)
        val gameTimeInput: EditText = findViewById(R.id.editGameTime)
        val radiusInput: Slider = findViewById(R.id.discreteSlider)

        val gameSessionRef = database.getReference("gameSessions")
        val query = gameSessionRef.orderByChild("sessionId").equalTo(lobbyCode)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val gameSessionSnapshot = snapshot.children.first()
                    val gameSession = gameSessionSnapshot.getValue(GameSessionClass::class.java)

                    gameSession?.let {
                        hidingTimeInput.setText(it.hidingTime.toString())
                        updateIntervalInput.setText(it.updateInterval.toString())
                        gameTimeInput.setText(it.gameLength.toString())
                        radiusInput.value = it.radius.toFloat()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@LobbySettings, "Error loading game settings", Toast.LENGTH_SHORT).show()
            }
        })
    }


    /**
     * Update game session with user input values
     */
    private fun confirmSettingsClicked(receivedLobbyCode: String?, receivedUsername: String?, host: Boolean?) {
        if (receivedLobbyCode == null) {
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

        if (updateInterval.isBlank() || hidingTime.isBlank() || gameTime.isBlank()) {
            Toast.makeText(this@LobbySettings, "All fields are required", Toast.LENGTH_SHORT).show()
            return
        } else if (updateInterval.toInt() < 1 || hidingTime.toInt() < 1 || gameTime.toInt() < 1){
            Toast.makeText(this@LobbySettings, "Input values have to be more than 0", Toast.LENGTH_SHORT).show()
            return
        }

        val query = database.getReference("gameSessions").orderByChild("sessionId").equalTo(receivedLobbyCode)
        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val gameSessionSnapshot = dataSnapshot.children.first()

                    val updatedGameSession = mapOf(
                        "gameStatus" to "ongoing",
                        "gameLength" to gameTime.toInt(),
                        "updateInterval" to updateInterval.toInt(),
                        "hidingTime" to hidingTime.toInt(),
                        "radius" to geofenceRadius.toInt(),
                        "geofenceLat" to userLatLng.latitude,
                        "geofenceLon" to userLatLng.longitude
                    )

                    gameSessionSnapshot.ref.updateChildren(updatedGameSession)
                        .addOnSuccessListener {
                            Toast.makeText(this@LobbySettings, "Game configurations updated", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@LobbySettings, Lobby::class.java)
                            intent.putExtra("lobby_key", receivedLobbyCode)
                            intent.putExtra("username_key", receivedUsername)
                            intent.putExtra("host", host)
                            startActivity(intent)
                            locationHelper.stopUpdate()
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this@LobbySettings, "Error updating game settings", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this@LobbySettings, "Lobby code not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@LobbySettings, "Error fetching data", Toast.LENGTH_SHORT).show()
            }
        })
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